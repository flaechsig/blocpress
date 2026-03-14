package io.github.flaechsig.blocpress.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark-Test: Misst die Durchsatzzeit für 100 Render-Requests mit 1, 2 und 3
 * LibreOffice-Worker-Instanzen.
 *
 * <p>Läuft gegen das All-in-one Quickstart-Image (PostgreSQL + Render + LibreOffice).
 *
 * <p>Ausführung:
 * <pre>
 *   mvn verify -pl blocpress-e2e -De2e.skip=false
 *
 *   # Mit einem lokalen Image:
 *   mvn verify -pl blocpress-e2e -De2e.skip=false -Dstudio.image=flaechsig/blocpress-studio-quickstart:latest
 * </pre>
 */
class PoolBenchmarkIT {

    private static final Logger LOG = LoggerFactory.getLogger(PoolBenchmarkIT.class);
    private static final String IMAGE = System.getProperty("studio.image",
            "flaechsig/blocpress-studio-quickstart:latest");
    private static final int RENDER_PORT = 8081;
    private static final int TOTAL_REQUESTS = 100;
    private static final int CONCURRENCY = 10;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void benchmarkWorkerScaling() throws Exception {
        Assumptions.assumeTrue(isDockerAvailable(), "Docker not available — skipping benchmark");

        byte[] templateBytes = loadInvoiceTemplate();
        String templateBase64 = Base64.getEncoder().encodeToString(templateBytes);
        List<String> payloads = buildPayloads(templateBase64);

        List<BenchmarkResult> results = new ArrayList<>();

        for (int workers : new int[]{1, 2, 3}) {
            LOG.info("=== Starting benchmark with {} worker(s) ===", workers);
            BenchmarkResult result = runWithWorkers(workers, payloads);
            results.add(result);
            LOG.info("Workers={}: total={}s avg={}ms min={}ms max={}ms p95={}ms",
                    workers,
                    String.format("%.1f", result.totalMs / 1000.0),
                    result.avgMs,
                    result.stats.getMin(),
                    result.stats.getMax(),
                    result.p95Ms);
        }

        printSummaryTable(results);
    }

    private BenchmarkResult runWithWorkers(int workers, List<String> payloads) throws Exception {
        try (GenericContainer<?> container = buildContainer(workers)) {
            container.start();
            String baseUrl = "http://" + container.getHost() + ":" + container.getMappedPort(RENDER_PORT);
            warmUp(baseUrl, payloads.getFirst());

            if (workers == 1) {
                saveSampleFiles(baseUrl, payloads);
            }

            ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
            List<CompletableFuture<Long>> futures = new ArrayList<>();
            long wallStart = System.currentTimeMillis();

            try (HttpClient client = HttpClient.newHttpClient()) {
                for (String payload : payloads) {
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        long t0 = System.currentTimeMillis();
                        try {
                            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/render/template"))
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                                    .timeout(Duration.ofSeconds(120))
                                    .build();
                            HttpResponse<byte[]> response = client.send(request,
                                    HttpResponse.BodyHandlers.ofByteArray());
                            if (response.statusCode() != 200) {
                                LOG.warn("Request failed with status {}", response.statusCode());
                            }
                        } catch (Exception e) {
                            LOG.warn("Request error: {}", e.getMessage());
                        }
                        return System.currentTimeMillis() - t0;
                    }, executor));
                }

                List<Long> durations = new ArrayList<>();
                for (CompletableFuture<Long> f : futures) {
                    durations.add(f.get(3, TimeUnit.MINUTES));
                }
                long totalMs = System.currentTimeMillis() - wallStart;

                executor.shutdown();
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOG.warn("Executor did not terminate within timeout");
                }

                return new BenchmarkResult(workers, totalMs, durations);
            }
        }
    }

    private void saveSampleFiles(String baseUrl, List<String> payloads) {
        try {
            Path sampleDir = Path.of("target/benchmark-samples");
            Path jsonDir = sampleDir.resolve("payloads");
            Path pdfDir = sampleDir.resolve("results");
            Files.createDirectories(jsonDir);
            Files.createDirectories(pdfDir);

            Path curlFile = sampleDir.resolve("render.sh");
            String curl = "#!/bin/bash\n" +
                    "# Benchmark-Sample-Request gegen den Render-Service\n" +
                    "# Erzeugt von PoolBenchmarkIT — bitte URL ggf. anpassen\n\n" +
                    "N=${1:-001}\n" +
                    "curl -s -X POST \\\n" +
                    "  -H 'Content-Type: application/json' \\\n" +
                    "  -d @payloads/payload-${N}.json \\\n" +
                    "  '" + baseUrl + "/api/render/template' \\\n" +
                    "  --output results/result-${N}.pdf\n";
            Files.writeString(curlFile, curl);
            if (!curlFile.toFile().setExecutable(true)) {
                LOG.warn("Could not set render.sh executable");
            }

            try (HttpClient client = HttpClient.newHttpClient()) {
                for (int i = 0; i < payloads.size(); i++) {
                    String payload = payloads.get(i);
                    String index = "%03d".formatted(i + 1);
                    Files.writeString(jsonDir.resolve("payload-" + index + ".json"), payload);

                    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/render/template"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .timeout(Duration.ofSeconds(60))
                            .build();
                    HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() == 200) {
                        Files.write(pdfDir.resolve("result-" + index + ".pdf"), response.body());
                    }
                }
            }

            LOG.info("Sample files saved to {} ({} payloads, {} PDFs)",
                    sampleDir.toAbsolutePath(), payloads.size(), payloads.size());
        } catch (Exception e) {
            LOG.warn("Could not save sample files: {}", e.getMessage());
        }
    }

    private void warmUp(String baseUrl, String payload) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (int attempt = 1; attempt <= 12; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/render/template"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .timeout(Duration.ofSeconds(30))
                            .build();
                    HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() == 200) {
                        LOG.info("Warm-up done after {} attempt(s) ({} bytes)", attempt, response.body().length);
                        return;
                    }
                    LOG.warn("Warm-up attempt {}/12: status={}", attempt, response.statusCode());
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    LOG.warn("Warm-up attempt {}/12 failed: {}", attempt, e.getMessage());
                    try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not create HttpClient for warm-up: {}", e.getMessage());
        }
        LOG.error("Warm-up failed after 12 attempts");
    }

    private static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private GenericContainer<?> buildContainer(int workers) {
        return new GenericContainer<>(DockerImageName.parse(IMAGE))
                .withExposedPorts(RENDER_PORT)
                .withEnv("BLOCPRESS_LO_WORKERS", String.valueOf(workers))
                .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("lo-" + workers))
                .waitingFor(
                        Wait.forHttp("/q/health/ready")
                                .forPort(RENDER_PORT)
                                .forStatusCode(200)
                                .withStartupTimeout(Duration.ofMinutes(4))
                );
    }

    private byte[] loadInvoiceTemplate() throws Exception {
        Path invoicePath = Path.of("../docs/samples/quickstart/invoice.odt");
        if (Files.exists(invoicePath)) {
            return Files.readAllBytes(invoicePath);
        }
        try (var is = getClass().getResourceAsStream("/invoice.odt")) {
            if (is != null) {
                return is.readAllBytes();
            }
        }
        throw new IllegalStateException("invoice.odt not found. Expected at: " + invoicePath.toAbsolutePath());
    }

    private List<String> buildPayloads(String templateBase64) throws Exception {
        List<String> payloads = new ArrayList<>();
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            int positionCount = (i % 10) + 1;
            String data = buildInvoiceJson(i, positionCount);
            ObjectNode body = MAPPER.createObjectNode();
            body.put("template", templateBase64);
            body.set("data", MAPPER.readTree(data));
            body.put("outputType", "pdf");
            payloads.add(MAPPER.writeValueAsString(body));
        }
        return payloads;
    }

    private String buildInvoiceJson(int index, int positionCount) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();

        ObjectNode invoice = root.putObject("invoice");
        invoice.put("number", "BP-2026-%04d".formatted(index + 1));
        invoice.put("date", "2026-03-14");
        invoice.put("currency", "€");
        invoice.put("paymentTermsDays", 14);

        ObjectNode customer = root.putObject("customer");
        customer.put("firstname", "Test");
        customer.put("lastname", "Kunde-%d".formatted(index));
        customer.put("street", "Teststraße %d".formatted(index + 1));
        customer.put("postcode", "44269");
        customer.put("city", "Dortmund");

        ArrayNode positions = root.putArray("positions");
        double netTotal = 0;
        for (int p = 0; p < positionCount; p++) {
            ObjectNode pos = positions.addObject();
            double unitPrice = 10.0 + p * 5.0;
            int qty = (p % 3) + 1;
            double total = unitPrice * qty;
            netTotal += total;
            pos.put("name", "Artikel-%d".formatted(p + 1));
            pos.put("description", "Beschreibung für Artikel %d".formatted(p + 1));
            pos.put("quantity", qty);
            pos.put("unitPrice", unitPrice);
            pos.put("total", total);
        }

        ObjectNode summary = root.putObject("summary");
        double taxAmount = netTotal * 0.19;
        summary.put("netTotal", Math.round(netTotal * 100.0) / 100.0);
        summary.put("taxRate", 19);
        summary.put("taxAmount", Math.round(taxAmount * 100.0) / 100.0);
        summary.put("grossTotal", Math.round((netTotal + taxAmount) * 100.0) / 100.0);

        return MAPPER.writeValueAsString(root);
    }

    private void printSummaryTable(List<BenchmarkResult> results) {
        LOG.info("");
        LOG.info("╔══════════════════════════════════════════════════════════════╗");
        LOG.info("║            LibreOffice Worker Pool — Benchmark               ║");
        LOG.info("║  {} Requests  │  {} parallel Clients                          ║",
                TOTAL_REQUESTS, CONCURRENCY);
        LOG.info("╠═════════╦═══════════╦══════════╦════════╦════════╦══════════╣");
        LOG.info("║ Workers ║  Gesamt   ║  Ø/Req   ║   Min  ║   Max  ║    P95   ║");
        LOG.info("╠═════════╬═══════════╬══════════╬════════╬════════╬══════════╣");
        BenchmarkResult baseline = results.getFirst();
        for (BenchmarkResult r : results) {
            double speedup = (double) baseline.totalMs / r.totalMs;
            LOG.info(String.format("║    %d    ║  %6.1fs  ║  %5dms ║ %4dms ║ %4dms ║  %5dms ║  (×%.1f)",
                    r.workers, r.totalMs / 1000.0, r.avgMs,
                    r.stats.getMin(), r.stats.getMax(), r.p95Ms, speedup));
        }
        LOG.info("╚═════════╩═══════════╩══════════╩════════╩════════╩══════════╝");
    }

    record BenchmarkResult(int workers, long totalMs, IntSummaryStatistics stats, long avgMs, long p95Ms) {
        BenchmarkResult(int workers, long totalMs, List<Long> durations) {
            this(workers, totalMs,
                    durations.stream().mapToInt(Long::intValue).summaryStatistics(),
                    (long) durations.stream().mapToLong(Long::longValue).average().orElse(0),
                    percentile(durations));
        }

        private static long percentile(List<Long> durations) {
            List<Long> sorted = durations.stream().sorted().toList();
            int index = (int) Math.ceil(95 / 100.0 * sorted.size()) - 1;
            return sorted.get(Math.max(0, index));
        }
    }
}
