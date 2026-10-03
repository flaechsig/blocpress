package io.github.flaechsig.blocpress.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lebenszyklus im Job-Pfad (US-0006): Webhook bei Erfolg und Fehler, zweistufige Bereinigung
 * (Ergebnis nach result-retention, Datensatz nach record-retention), Neu-Einreihen haengender Jobs.
 */
@QuarkusTest
class AsyncJobLifecycleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TestHTTPResource("/")
    URI base;

    @Inject
    RenderJobWorker worker;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<JsonNode> callbacks = new CopyOnWriteArrayList<>();
    private HttpServer webhookReceiver;

    @BeforeEach
    void startReceiver() throws Exception {
        webhookReceiver = HttpServer.create(new InetSocketAddress(0), 0);
        webhookReceiver.createContext("/hook", exchange -> {
            callbacks.add(MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        webhookReceiver.start();
    }

    @AfterEach
    void stopReceiver() {
        webhookReceiver.stop(0);
    }

    @Test
    void webhookIsCalledWhenJobSucceeds() throws Exception {
        String name = importTemplate();
        String id = submit(name);

        JsonNode callback = awaitCallback(id);
        assertEquals("DONE", callback.path("status").asText(), callback.toString());
        assertEquals("DONE", status(id).path("status").asText());
    }

    @Test
    void webhookIsCalledWhenJobFails() throws Exception {
        String id = submit("gibt-es-nicht-" + UUID.randomUUID());

        JsonNode callback = awaitCallback(id);
        assertEquals("FAILED", callback.path("status").asText(), callback.toString());
        JsonNode status = status(id);
        assertEquals("FAILED", status.path("status").asText());
        assertTrue(!status.path("errorMessage").asText().isBlank(), "Fehlermeldung fehlt: " + status);
    }

    @Test
    void cleanupRemovesResultsFirstAndRecordsLater() {
        LocalDateTime now = LocalDateTime.now();
        UUID recent = job(RenderJobStatus.DONE, now.minusHours(1), now.minusHours(1), true);
        UUID resultExpired = job(RenderJobStatus.DONE, now.minusDays(2), now.minusDays(2), true);
        UUID recordExpired = job(RenderJobStatus.DONE, now.minusDays(8), now.minusDays(8), true);

        worker.cleanupJobs();

        QuarkusTransaction.requiringNew().run(() -> {
            RenderJob kept = RenderJob.findById(recent);
            assertNotNull(kept.result, "juengere Ergebnisse bleiben erhalten");
            RenderJob cleared = RenderJob.findById(resultExpired);
            assertNotNull(cleared, "Datensatz bleibt bis record-retention");
            assertNull(cleared.result, "Ergebnis-Bytes nach result-retention geloescht");
            assertNull(RenderJob.findById(recordExpired), "Datensatz nach record-retention geloescht");
        });
    }

    @Test
    void staleProcessingJobIsRequeued() {
        LocalDateTime now = LocalDateTime.now();
        UUID stale = job(RenderJobStatus.PROCESSING, now.minusHours(1), now.minusMinutes(30), false);
        UUID active = job(RenderJobStatus.PROCESSING, now.minusMinutes(1), now.minusMinutes(1), false);

        worker.requeueStaleJobs();

        QuarkusTransaction.requiringNew().run(() -> {
            // der wieder eingereihte Job kann vom Worker sofort erneut geholt werden — nur nicht mehr "haengend"
            RenderJob requeued = RenderJob.findById(stale);
            assertTrue(requeued.status != RenderJobStatus.PROCESSING || requeued.updatedAt.isAfter(now.minusMinutes(1)),
                    "haengender Job nicht neu eingereiht: " + requeued.status + " " + requeued.updatedAt);
            assertEquals(RenderJobStatus.PROCESSING, ((RenderJob) RenderJob.findById(active)).status,
                    "aktiver Job darf nicht angefasst werden");
        });
    }

    private UUID job(RenderJobStatus status, LocalDateTime created, LocalDateTime updated, boolean withResult) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            RenderJob job = new RenderJob();
            job.id = id;
            job.templateName = "lifecycle-test";
            job.data = "{}";
            job.status = status;
            job.result = withResult ? new byte[]{1, 2, 3} : null;
            job.createdAt = created;
            job.updatedAt = updated;
            job.persist();
        });
        return id;
    }

    private JsonNode awaitCallback(String jobId) throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            for (JsonNode c : callbacks) {
                if (jobId.equals(c.path("jobId").asText())) {
                    return c;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("kein Webhook fuer Job " + jobId + " — empfangen: " + callbacks);
    }

    private String importTemplate() throws Exception {
        String name = "lifecycle-" + UUID.randomUUID();
        byte[] odt;
        try (InputStream is = getClass().getResourceAsStream("/kuendigung.odt")) {
            odt = is.readAllBytes();
        }
        var body = MAPPER.createObjectNode().put("id", UUID.randomUUID().toString()).put("name", name).put("version", 1)
                .put("contentBase64", Base64.getEncoder().encodeToString(odt))
                .put("validFrom", LocalDateTime.now().minusDays(1).withNano(0).toString());
        assertEquals(200, post("/api/render/templates/import", body.toString()).statusCode());
        return name;
    }

    private String submit(String templateName) throws Exception {
        String hook = "http://localhost:" + webhookReceiver.getAddress().getPort() + "/hook";
        var response = post("/api/render/jobs", "{\"templateName\":\"" + templateName
                + "\",\"outputType\":\"pdf\",\"data\":{},\"webhookUrl\":\"" + hook + "\"}");
        assertEquals(202, response.statusCode(), response.body());
        return MAPPER.readTree(response.body()).path("id").asText();
    }

    private JsonNode status(String id) throws Exception {
        return MAPPER.readTree(http.send(HttpRequest.newBuilder(base.resolve("/api/render/jobs/" + id)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body());
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(base.resolve(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
