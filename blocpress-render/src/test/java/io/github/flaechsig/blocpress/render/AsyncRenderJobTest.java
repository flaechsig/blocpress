package io.github.flaechsig.blocpress.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Job-Pfad Ende-zu-Ende (US-0006): Vorlage importieren, Job einreichen (202 + Job-ID),
 * Status bis DONE abfragen, Ergebnis abholen.
 *
 * <p>Der Native-Fehler aus 2.5.0/2.5.1 (HTTP 500, weil {@code JobStatus} nicht fuer Reflection
 * registriert war) tritt nur im Native-Image auf — dort deckt ihn {@code RenderLoadIT} mit
 * {@code -Dload.async=true} ab.</p>
 */
@QuarkusTest
class AsyncRenderJobTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @TestHTTPResource("/")
    URI base;

    @Test
    void submitPollAndFetchResult() throws Exception {
        String name = "async-test-" + UUID.randomUUID();
        byte[] odt;
        try (InputStream is = getClass().getResourceAsStream("/kuendigung.odt")) {
            odt = is.readAllBytes();
        }
        var importBody = MAPPER.createObjectNode()
                .put("id", UUID.randomUUID().toString())
                .put("name", name)
                .put("version", 1)
                .put("contentBase64", Base64.getEncoder().encodeToString(odt))
                .put("validFrom", LocalDateTime.now().minusDays(1).withNano(0).toString());
        assertEquals(200, post("/api/render/templates/import", importBody.toString()).statusCode());

        var submit = post("/api/render/jobs",
                "{\"templateName\":\"" + name + "\",\"outputType\":\"pdf\",\"data\":{}}");
        assertEquals(202, submit.statusCode(), submit.body());
        String id = MAPPER.readTree(submit.body()).path("id").asText();
        assertFalse(id.isBlank(), "Antwort ohne Job-ID: " + submit.body());

        JsonNode status = null;
        for (int i = 0; i < 60; i++) {   // Worker pollt alle 2 s
            var response = http.send(HttpRequest.newBuilder(base.resolve("/api/render/jobs/" + id)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            status = MAPPER.readTree(response.body());
            if (status.path("status").asText().matches("DONE|FAILED")) {
                break;
            }
            Thread.sleep(500);
        }
        assertEquals("DONE", status.path("status").asText(), status.toString());

        var result = http.send(HttpRequest.newBuilder(base.resolve("/api/render/jobs/" + id + "/result")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, result.statusCode());
        assertTrue(new String(result.body(), 0, 5).startsWith("%PDF-"), "Ergebnis ist kein PDF");
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(base.resolve(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
