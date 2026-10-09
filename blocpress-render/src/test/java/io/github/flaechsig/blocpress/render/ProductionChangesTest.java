package io.github.flaechsig.blocpress.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aenderungen in production wirken sofort (REQ-0063), auch wenn eine andere render-Instanz sie
 * angenommen hat: Der Test aendert die Datenbank direkt, ohne den Cache dieser Instanz zu leeren.
 * Gueltigkeitsgrenzen wirken auch fuer zwischengespeicherte Inhalte (REQ-0038).
 * v1 = IfCondition.odt ("Liebe Frau Müller"), v2 = section.odt ("Absatz der unter der Bedingung").
 */
@QuarkusTest
class ProductionChangesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path CORE = Path.of("../blocpress-core/src/test/resources");
    private static final String DATA = "{\"data\":{\"kunde\":{\"anrede\":\"FRAU\",\"nachname\":\"Müller\"}},\"outputType\":\"pdf\"}";
    private static final String V1 = "Liebe Frau Müller";
    private static final String V2 = "Absatz der unter der Bedingung";

    private final HttpClient http = HttpClient.newHttpClient();

    @TestHTTPResource("/")
    URI base;

    @Test
    @DisplayName("REQ-0063: removalByAnotherInstanceTakesEffectImmediately")
    void removalByAnotherInstanceTakesEffectImmediately() throws Exception {
        String name = "removed-" + UUID.randomUUID();
        importTemplate(name, 1, "IfCondition.odt", LocalDateTime.now().minusDays(1), null);
        assertTrue(text(render(name)).contains(V1), "v1 erwartet");   // fuellt den Cache

        QuarkusTransaction.requiringNew().run(() -> ProductionTemplate.delete("name", name));

        assertEquals(404, render(name).statusCode(), "entfernte Vorlage wird weiter ausgeliefert");
    }

    @Test
    @DisplayName("REQ-0063: newVersionFromAnotherInstanceIsUsedImmediately")
    void newVersionFromAnotherInstanceIsUsedImmediately() throws Exception {
        String name = "replaced-" + UUID.randomUUID();
        importTemplate(name, 1, "IfCondition.odt", LocalDateTime.now().minusDays(1), null);
        assertTrue(text(render(name)).contains(V1), "v1 erwartet");   // fuellt den Cache

        byte[] v2 = Files.readAllBytes(CORE.resolve("section.odt"));
        QuarkusTransaction.requiringNew().run(() -> {
            ProductionTemplate t = new ProductionTemplate();
            t.id = UUID.randomUUID();
            t.name = name;
            t.version = 2;
            t.content = v2;
            t.validFrom = LocalDateTime.now().minusMinutes(1);
            t.persist();
        });

        assertTrue(text(render(name)).contains(V2), "neue Version einer anderen Instanz wird nicht genutzt");
    }

    @Test
    @DisplayName("REQ-0038: expiryWhileCachedTakesEffectImmediately")
    void expiryWhileCachedTakesEffectImmediately() throws Exception {
        String name = "expiring-" + UUID.randomUUID();
        LocalDateTime until = LocalDateTime.now().withNano(0).plusSeconds(3);
        importTemplate(name, 1, "IfCondition.odt", LocalDateTime.now().minusDays(1), until);
        assertTrue(text(render(name)).contains(V1), "v1 erwartet");   // fuellt den Cache

        waitUntil(until);

        assertEquals(404, render(name).statusCode(), "abgelaufene Version aus dem Cache ausgeliefert");
    }

    @Test
    @DisplayName("REQ-0038: nextVersionTakesOverAtItsValidFrom")
    void nextVersionTakesOverAtItsValidFrom() throws Exception {
        String name = "switching-" + UUID.randomUUID();
        LocalDateTime switchAt = LocalDateTime.now().withNano(0).plusSeconds(3);
        importTemplate(name, 1, "IfCondition.odt", LocalDateTime.now().minusDays(1), null);
        importTemplate(name, 2, "section.odt", switchAt, null);
        assertTrue(text(render(name)).contains(V1), "vor dem Wechsel gilt v1");   // fuellt den Cache

        waitUntil(switchAt);

        assertTrue(text(render(name)).contains(V2), "nach dem Wechsel muss v2 gelten");
    }

    private static void waitUntil(LocalDateTime moment) throws InterruptedException {
        while (!LocalDateTime.now().isAfter(moment.plusNanos(200_000_000))) {
            Thread.sleep(100);
        }
    }

    private void importTemplate(String name, int version, String file, LocalDateTime from, LocalDateTime until) throws Exception {
        var body = MAPPER.createObjectNode()
                .put("id", UUID.randomUUID().toString())
                .put("name", name)
                .put("version", version)
                .put("contentBase64", Base64.getEncoder().encodeToString(Files.readAllBytes(CORE.resolve(file))))
                .put("validFrom", from.withNano(0).toString());
        if (until != null) {
            body.put("validUntil", until.withNano(0).toString());
        }
        var response = http.send(HttpRequest.newBuilder(base.resolve("/api/render/templates/import"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
    }

    private HttpResponse<byte[]> render(String name) throws Exception {
        return http.send(HttpRequest.newBuilder(base.resolve("/api/render/" + name))
                .header("Content-Type", "application/json")
                .header("Accept", "application/pdf")
                .POST(HttpRequest.BodyPublishers.ofString(DATA)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String text(HttpResponse<byte[]> response) throws Exception {
        assertEquals(200, response.statusCode(), new String(response.body()));
        try (PDDocument doc = PDDocument.load(response.body())) {
            return new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
        }
    }
}
