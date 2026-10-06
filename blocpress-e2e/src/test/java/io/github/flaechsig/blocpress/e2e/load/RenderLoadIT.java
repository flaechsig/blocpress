package io.github.flaechsig.blocpress.e2e.load;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Wiederholbarer Lasttest fuer blocpress-render — nie Teil des normalen Builds.
 *
 * <pre>
 *   # Docker (Default): startet render selbst, je CPU-/Worker-Kombination und Stufe frisch
 *   mvn verify -pl blocpress-e2e -Pload -Dload.image=flaechsig/blocpress-render:2.5.1 \
 *       -Dload.cpus=0.5,1,2 -Dload.workers=2,4 -Dload.levels=1,2,4,8,16
 *
 *   # laufende Instanz (z.B. docker run), cgroup + Neustart ueber den Containernamen
 *   mvn verify -pl blocpress-e2e -Pload -Dload.mode=external -Dload.url=http://localhost:8080 \
 *       -Dload.docker.container=blocpress-render
 *
 *   # Kubernetes: Deployment wird je Stufe neu gestartet, Port-Forward macht der Test
 *   mvn verify -pl blocpress-e2e -Pload -Dload.mode=k8s -Dload.k8s.namespace=blocpress \
 *       -Dload.k8s.deployment=blocpress-render -Dload.k8s.selector=app=blocpress-render
 * </pre>
 *
 * <p>Je Stufe: frische Instanz (Speicherspitze), sequenzieller Referenzlauf ueber alle Szenarien
 * (zugleich Aufwaermen), dann N parallele Clients ueber den Lastmix. Jeder Render wird inhaltlich
 * geprueft ({@link Scenario#check}). Der Test schlaegt fehl, wenn ein Render mit HTTP 200 einen
 * falschen Inhalt liefert; HTTP-Fehler und Timeouts sind Messergebnis und stehen im Report.
 * Anleitung zur Auswertung: {@code docs/guides/render-sizing.md}.</p>
 */
class RenderLoadIT {

    private static final Logger LOG = LoggerFactory.getLogger(RenderLoadIT.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LoadConfig config = LoadConfig.fromSystemProperties();
    private final List<LevelResult> results = new ArrayList<>();

    @Test
    void measure() throws Exception {
        List<Scenario> scenarios = Scenario.load();
        LOG.info("Lastmix: {}", scenarios.stream().map(Scenario::name).toList());

        switch (config.mode()) {
            case "docker" -> measureDocker(scenarios);
            case "external" -> measure(new RenderTarget.External(config.url(), config.dockerContainer(),
                    config.restart()), scenarios);
            case "k8s" -> measure(new RenderTarget.Kubernetes(config.k8sNamespace(), config.k8sDeployment(),
                    config.k8sSelector(), config.restart()), scenarios);
            default -> throw new IllegalArgumentException("load.mode unbekannt: " + config.mode());
        }

        writeReport();
        long contentErrors = results.stream().mapToLong(LevelResult::contentErrors).sum();
        assertEquals(0, contentErrors, "Renders mit HTTP 200, aber falschem Inhalt — siehe " + config.report());
    }

    private void measureDocker(List<Scenario> scenarios) throws Exception {
        try (Network network = Network.newNetwork();
             GenericContainer<?> postgres = RenderTarget.Docker.postgres(network)) {
            postgres.start();
            for (double cpus : config.cpus()) {
                for (int workers : config.workers()) {
                    measure(new RenderTarget.Docker(config.image(), cpus, config.memoryBytes(), workers, network),
                            scenarios);
                }
            }
        }
    }

    private void measure(RenderTarget target, List<Scenario> scenarios) throws Exception {
        try (target; HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            for (int parallel : config.levels()) {
                target.restart();
                Map<String, String> reference = referencePass(http, target, scenarios);
                results.add(runSync(http, target, scenarios, reference, parallel));
                LOG.info(results.getLast().row());
            }
            if (config.async()) {
                target.restart();
                Map<String, String> reference = referencePass(http, target, scenarios);
                results.add(runAsync(http, target, scenarios, reference));
                LOG.info(results.getLast().row());
            }
        }
    }

    /** Sequenziell je Szenario ein Render: waermt LibreOffice auf und liefert die Referenz-Wortfolge. */
    private Map<String, String> referencePass(HttpClient http, RenderTarget target, List<Scenario> scenarios)
            throws Exception {
        Map<String, String> reference = new HashMap<>();
        for (Scenario s : scenarios) {
            var response = renderSync(http, target, s);
            String error = response.statusCode() == 200 ? s.check(response.body(), null) : "HTTP " + response.statusCode();
            if (error != null) {
                LOG.error("Referenzlauf {} fehlerhaft: {}", s.name(), error);
                continue; // ohne Referenz greifen fuer dieses Szenario nur Pflicht-/Verbotstexte
            }
            reference.put(s.name(), Scenario.words(response.body()));
        }
        return reference;
    }

    private LevelResult runSync(HttpClient http, RenderTarget target, List<Scenario> scenarios,
                                Map<String, String> reference, int parallel) throws Exception {
        int total = config.requestsFor(parallel);
        AtomicInteger next = new AtomicInteger();
        List<LevelResult.Sample> samples = java.util.Collections.synchronizedList(new ArrayList<>());

        CgroupStats before = target.stats();
        long start = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(parallel)) {
            List<Future<?>> clients = new ArrayList<>();
            for (int c = 0; c < parallel; c++) {
                clients.add(pool.submit(() -> {
                    int i;
                    while ((i = next.getAndIncrement()) < total) {
                        Scenario s = scenarios.get(i % scenarios.size());
                        long t0 = System.nanoTime();
                        String error;
                        boolean contentError = false;
                        try {
                            var response = renderSync(http, target, s);
                            if (response.statusCode() != 200) {
                                error = "HTTP " + response.statusCode();
                            } else {
                                error = s.check(response.body(), reference.get(s.name()));
                                contentError = error != null;
                            }
                        } catch (HttpTimeoutException e) {
                            error = "Timeout > " + config.timeout().toSeconds() + "s";
                        } catch (Exception e) {
                            error = e.getClass().getSimpleName();
                        }
                        samples.add(new LevelResult.Sample(s.name(), seconds(t0), error, contentError));
                    }
                    return null;
                }));
            }
            for (Future<?> f : clients) {
                f.get();
            }
        }
        double wall = seconds(start);
        CgroupStats after = target.stats();
        return new LevelResult(target.describe(), target.workers(), "sync", parallel, List.copyOf(samples), wall,
                before, after, target.afterLevelNote());
    }

    /**
     * Job-Pfad: Vorlagen importieren, alle Jobs auf einmal einreichen, bis DONE/FAILED pollen,
     * Ergebnis inhaltlich pruefen. Dauer = Einreichen bis fertig (inkl. Warteschlange).
     */
    private LevelResult runAsync(HttpClient http, RenderTarget target, List<Scenario> scenarios,
                                 Map<String, String> reference) throws Exception {
        for (Scenario s : scenarios) {
            var importBody = MAPPER.createObjectNode()
                    .put("id", UUID.randomUUID().toString())
                    .put("name", s.templateName())
                    .put("version", 1)
                    .put("contentBase64", Base64.getEncoder().encodeToString(s.template()))
                    .put("validFrom", LocalDateTime.now().minusDays(1).withNano(0).toString());
            var response = http.send(post(target, "/api/render/templates/import", importBody.toString()),
                    HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Import " + s.name() + " -> HTTP " + response.statusCode());
            }
        }

        int total = config.requestsFor(1);
        Map<String, Long> submitted = new HashMap<>();
        Map<String, Scenario> scenarioOf = new HashMap<>();
        List<LevelResult.Sample> samples = new ArrayList<>();
        CgroupStats before = target.stats();
        long start = System.nanoTime();
        for (int i = 0; i < total; i++) {
            Scenario s = scenarios.get(i % scenarios.size());
            var body = MAPPER.createObjectNode().put("templateName", s.templateName()).put("outputType", "pdf");
            body.set("data", s.data());
            long t0 = System.nanoTime();
            var response = http.send(post(target, "/api/render/jobs", body.toString()),
                    HttpResponse.BodyHandlers.ofString());
            String id = response.statusCode() == 202 ? MAPPER.readTree(response.body()).path("id").asText("") : "";
            if (id.isEmpty()) {
                // ohne Job-ID kann ein Client den Job nicht verfolgen — als Fehler werten
                samples.add(new LevelResult.Sample(s.name(), seconds(t0),
                        "Einreichen: HTTP " + response.statusCode() + " ohne Job-ID", false));
                continue;
            }
            submitted.put(id, t0);
            scenarioOf.put(id, s);
        }

        long deadline = System.nanoTime() + config.timeout().toNanos() + total * 3_000_000_000L;
        while (!submitted.isEmpty() && System.nanoTime() < deadline) {
            for (var it = submitted.entrySet().iterator(); it.hasNext(); ) {
                var job = it.next();
                JsonNode status = MAPPER.readTree(http.send(get(target, "/api/render/jobs/" + job.getKey()),
                        HttpResponse.BodyHandlers.ofString()).body());
                String state = status.path("status").asText();
                if (!state.equals("DONE") && !state.equals("FAILED")) {
                    continue;
                }
                Scenario s = scenarioOf.get(job.getKey());
                String error = "Job FAILED: " + status.path("errorMessage").asText();
                boolean contentError = false;
                if (state.equals("DONE")) {
                    byte[] pdf = http.send(get(target, "/api/render/jobs/" + job.getKey() + "/result"),
                            HttpResponse.BodyHandlers.ofByteArray()).body();
                    error = s.check(pdf, reference.get(s.name()));
                    contentError = error != null;
                }
                samples.add(new LevelResult.Sample(s.name(), seconds(job.getValue()), error, contentError));
                it.remove();
            }
            Thread.sleep(250);
        }
        submitted.forEach((id, t0) -> samples.add(new LevelResult.Sample(scenarioOf.get(id).name(),
                seconds(t0), "Timeout (Job nicht fertig)", false)));
        double wall = seconds(start);
        CgroupStats after = target.stats();
        return new LevelResult(target.describe(), target.workers(), "async (Jobs)", total, samples, wall,
                before, after, target.afterLevelNote());
    }

    private HttpResponse<byte[]> renderSync(HttpClient http, RenderTarget target, Scenario s) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(target.baseUrl() + "/api/render/template"))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/pdf")
                        .timeout(config.timeout())
                        .POST(HttpRequest.BodyPublishers.ofString(s.renderRequestJson()))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpRequest post(RenderTarget target, String path, String json) {
        return HttpRequest.newBuilder(URI.create(target.baseUrl() + path))
                .header("Content-Type", "application/json")
                .timeout(config.timeout())
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
    }

    private HttpRequest get(RenderTarget target, String path) {
        return HttpRequest.newBuilder(URI.create(target.baseUrl() + path)).timeout(config.timeout()).GET().build();
    }

    private static double seconds(long startNanos) {
        return (System.nanoTime() - startNanos) / 1e9;
    }

    private void writeReport() throws Exception {
        StringBuilder md = new StringBuilder("# blocpress-render — Lastmessung\n\n")
                .append("Erzeugt von `RenderLoadIT` am ").append(LocalDateTime.now().withNano(0))
                .append(" · Modus `").append(config.mode()).append("`")
                .append(config.mode().equals("docker") ? " · Image `" + config.image() + "`" : "")
                .append(" · Timeout ").append(config.timeout().toSeconds()).append(" s\n\n")
                .append("Speicherspitze = `memory.peak` (frische Instanz je Stufe, inkl. Referenzlauf); ")
                .append("CPU-s und gedrosselt = Differenz aus `cpu.stat` waehrend der Stufe ")
                .append("(gedrosselte Sekunden sind ueber CPUs summiert — Vergleichswert, keine Wartezeit).\n\n")
                .append(LevelResult.header()).append('\n');
        results.forEach(r -> md.append(r.row()).append('\n'));

        StringBuilder errors = new StringBuilder();
        for (LevelResult r : results) {
            r.errorCounts().forEach((error, count) -> errors.append("- ").append(r.config()).append(", N=")
                    .append(r.parallel()).append(" (").append(r.label()).append("): ").append(error)
                    .append(" × ").append(count).append('\n'));
        }
        md.append("\n## Fehler\n\n").append(errors.isEmpty() ? "keine\n" : errors);

        Path report = Path.of(config.report());
        Files.createDirectories(report.toAbsolutePath().getParent());
        Files.writeString(report, md);
        LOG.info("\n{}\nReport: {}", md, report.toAbsolutePath());
    }
}
