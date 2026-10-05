package io.github.flaechsig.blocpress.render;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Rendern einer freigegebenen Vorlage per Name (US-0005): es gilt die juengste Version, deren
 * validFrom erreicht und deren validUntil nicht ueberschritten ist. Abgelaufene oder unbekannte
 * Vorlagen ergeben 404 (US-0019). Ersetzt die frueheren Platzhalter-Tests (assertTrue(true)).
 */
@QuarkusTest
class RenderByNameTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path CORE = Path.of("../blocpress-core/src/test/resources");
    private static final String DATA = "{\"data\":{\"kunde\":{\"anrede\":\"FRAU\",\"nachname\":\"Müller\"}},\"outputType\":\"pdf\"}";

    private final HttpClient http = HttpClient.newHttpClient();

    @TestHTTPResource("/")
    URI base;

    @Test
    void latestActiveVersionIsUsedAndFutureVersionsAreNot() throws Exception {
        String name = "byname-" + UUID.randomUUID();
        // v1 (aelter): bedingter Text "Liebe Frau Müller"; v2 (juenger, aktiv): Bereich-Vorlage; v3 (kuenftig)
        importTemplate(name, 1, "IfCondition.odt", LocalDateTime.now().minusDays(2), null);
        importTemplate(name, 2, "section.odt", LocalDateTime.now().minusDays(1), null);
        importTemplate(name, 3, "IfCondition.odt", LocalDateTime.now().plusDays(5), null);

        var response = render(name);
        assertEquals(200, response.statusCode());
        String text = pdfText(response.body());
        assertTrue(text.contains("Absatz der unter der Bedingung"), "erwartet v2 (section.odt): " + text);
    }

    @Test
    void newlyImportedVersionIsUsedImmediately() throws Exception {
        // Vorlagen-Cache: nach dem Import einer neueren Version darf nicht mehr die alte geliefert werden
        String name = "cache-" + UUID.randomUUID();
        importTemplate(name, 1, "IfCondition.odt", LocalDateTime.now().minusDays(2), null);
        assertTrue(pdfText(render(name).body()).contains("Liebe Frau Müller"), "v1 erwartet");

        importTemplate(name, 2, "section.odt", LocalDateTime.now().minusDays(1), null);
        String text = pdfText(render(name).body());
        assertTrue(text.contains("Absatz der unter der Bedingung"), "nach Import muss v2 gelten (Cache): " + text);
    }

    @Test
    @DisplayName("REQ-0022: expiredTemplateIsBlocked")
    void expiredTemplateIsBlocked() throws Exception {
        String name = "expired-" + UUID.randomUUID();
        importTemplate(name, 1, "IfCondition.odt", LocalDateTime.now().minusYears(1), LocalDateTime.now().minusDays(1));

        assertEquals(404, render(name).statusCode(), "abgelaufene Vorlage darf nicht gerendert werden");
    }

    @Test
    void unknownTemplateGives404() throws Exception {
        assertEquals(404, render("gibt-es-nicht-" + UUID.randomUUID()).statusCode());
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

    private static String pdfText(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
        }
    }
}
