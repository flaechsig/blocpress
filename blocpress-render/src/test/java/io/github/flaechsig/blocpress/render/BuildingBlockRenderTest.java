package io.github.flaechsig.blocpress.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static io.github.flaechsig.blocpress.render.TestDocumentUtil.extractPdfText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bausteine zur Renderzeit aus production (ADR-0018). {@code vorlage-mit-baustein.odt} verknuepft
 * einen Baustein unter dem Host {@code unreachable.invalid}: wuerde render die Verknuepfung oeffnen,
 * schluege das Rendern fehl. Jeder Test ersetzt den Namen im Link durch einen eigenen.
 */
@QuarkusTest
class BuildingBlockRenderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LINK = "http://unreachable.invalid/api/webdav/released/bausteine/Sondervereinbarung.odt";
    private static final String DATA = """
            {"customer": [{"firstName": "Michael", "lastName": "Miller",
              "address": {"street": "Example Street 1", "postcode": "12345", "city": "Hamburg"}}]}
            """;

    private final HttpClient http = HttpClient.newHttpClient();

    @TestHTTPResource("/")
    URI base;

    @Test
    @DisplayName("REQ-0033: Baustein wird per Name aus production eingesetzt, ohne den Link zu oeffnen")
    void buildingBlockIsInlinedFromProductionByName() throws Exception {
        String block = "SV-" + UUID.randomUUID();
        String template = "T-" + UUID.randomUUID();
        importItem(block, 1, resource("sondervereinbarung.odt"), "BAUSTEIN", LocalDateTime.now().minusDays(2), null);
        importItem(template, 1, linkTo("http://unreachable.invalid/api/webdav/released/bausteine/" + block + ".odt"),
                "TEMPLATE", LocalDateTime.now().minusDays(1), null);

        var response = renderByName(template);
        assertEquals(200, response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        String text = pdfText(response.body());
        assertTrue(text.contains("Special Agreement with Michael Miller"), text);

        // Host und Praefix (/design/) sind ohne Bedeutung; es zaehlt der Name
        String other = "T-" + UUID.randomUUID();
        importItem(other, 1, linkTo("https://anderer-host.invalid:9999/x/api/webdav/design/bausteine/" + block + ".odt"),
                "TEMPLATE", LocalDateTime.now().minusDays(1), null);
        var otherResponse = renderByName(other);
        assertEquals(200, otherResponse.statusCode(), new String(otherResponse.body(), StandardCharsets.UTF_8));
        text = pdfText(otherResponse.body());
        assertTrue(text.contains("Special Agreement with Michael Miller"), text);

        // spaet aufgeloest: eine neuere gueltige Version des Bausteins wirkt ohne neue Freigabe der Vorlage
        importItem(block, 2, replaceText(resource("sondervereinbarung.odt"), "Special Agreement", "Revised Agreement"),
                "BAUSTEIN", LocalDateTime.now().minusHours(1), null);
        text = pdfText(renderByName(template).body());
        assertTrue(text.contains("Revised Agreement with Michael Miller"), text);
    }

    @Test
    @DisplayName("REQ-0034: unbekannter, abgelaufener oder fremd verlinkter Baustein ergibt 422 ohne technische Details")
    void unknownOrForeignBuildingBlockIsRejected() throws Exception {
        String unknown = "T-" + UUID.randomUUID();
        importItem(unknown, 1, linkTo("http://unreachable.invalid/api/webdav/released/bausteine/fehlt-" + UUID.randomUUID() + ".odt"),
                "TEMPLATE", LocalDateTime.now().minusDays(1), null);
        assertRejectedWithoutDetails(renderByName(unknown));

        String expiredBlock = "SV-" + UUID.randomUUID();
        importItem(expiredBlock, 1, resource("sondervereinbarung.odt"), "BAUSTEIN",
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1));
        String expired = "T-" + UUID.randomUUID();
        importItem(expired, 1, linkTo("http://unreachable.invalid/api/webdav/released/bausteine/" + expiredBlock + ".odt"),
                "TEMPLATE", LocalDateTime.now().minusDays(1), null);
        assertRejectedWithoutDetails(renderByName(expired));

        String fileLink = "T-" + UUID.randomUUID();
        importItem(fileLink, 1, resource("vorlage-mit-dateilink.odt"), "TEMPLATE", LocalDateTime.now().minusDays(1), null);
        assertRejectedWithoutDetails(renderByName(fileLink));
    }

    @Test
    @DisplayName("REQ-0035: mitgeschickte Vorlage mit Verknuepfung ergibt 422 (JSON und multipart)")
    void templateSentWithRequestMustNotLinkBuildingBlocks() throws Exception {
        byte[] odt = resource("vorlage-mit-baustein.odt");

        var json = MAPPER.createObjectNode()
                .put("template", Base64.getEncoder().encodeToString(odt))
                .put("outputType", "odt");
        json.set("data", MAPPER.readTree(DATA));
        var jsonResponse = http.send(HttpRequest.newBuilder(base.resolve("/api/render/template"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/vnd.oasis.opendocument.text")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString())).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(422, jsonResponse.statusCode(), jsonResponse.body());

        String boundary = "b" + UUID.randomUUID();
        var multipart = new ByteArrayOutputStream();
        multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"template\"; filename=\"t.odt\"\r\n"
                + "Content-Type: application/vnd.oasis.opendocument.text\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        multipart.write(odt);
        multipart.write(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"data\"\r\n\r\n" + DATA
                + "\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var multipartResponse = http.send(HttpRequest.newBuilder(base.resolve("/api/render/template"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/pdf")
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(422, multipartResponse.statusCode(), multipartResponse.body());
    }

    @Test
    @DisplayName("REQ-0036: per Name nur Vorlagen, eingebunden nur Bausteine")
    void templatesAndBuildingBlocksAreKeptApart() throws Exception {
        String block = "SV-" + UUID.randomUUID();
        importItem(block, 1, resource("sondervereinbarung.odt"), "BAUSTEIN", LocalDateTime.now().minusDays(1), null);
        assertEquals(404, renderByName(block).statusCode(), "ein Baustein darf nicht per Name gerendert werden");

        String templateOnly = "SV-" + UUID.randomUUID();
        importItem(templateOnly, 1, resource("sondervereinbarung.odt"), "TEMPLATE", LocalDateTime.now().minusDays(1), null);
        String linking = "T-" + UUID.randomUUID();
        importItem(linking, 1, linkTo("http://unreachable.invalid/api/webdav/released/bausteine/" + templateOnly + ".odt"),
                "TEMPLATE", LocalDateTime.now().minusDays(1), null);
        assertEquals(422, renderByName(linking).statusCode(), "eine Vorlage darf nicht als Baustein eingebunden werden");
    }

    private void assertRejectedWithoutDetails(HttpResponse<byte[]> response) {
        String body = new String(response.body(), StandardCharsets.UTF_8);
        assertEquals(422, response.statusCode(), body);
        assertFalse(body.contains("unreachable.invalid"), body);
        assertFalse(body.contains("/etc/"), body);
        assertFalse(body.contains("Exception"), body);
    }

    private void importItem(String name, int version, byte[] odt, String type, LocalDateTime from, LocalDateTime until)
            throws Exception {
        var body = MAPPER.createObjectNode()
                .put("id", UUID.randomUUID().toString())
                .put("name", name)
                .put("version", version)
                .put("type", type)
                .put("contentBase64", Base64.getEncoder().encodeToString(odt))
                .put("validFrom", from.withNano(0).toString());
        if (until != null) {
            body.put("validUntil", until.withNano(0).toString());
        }
        var response = http.send(HttpRequest.newBuilder(base.resolve("/api/render/templates/import"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
    }

    private HttpResponse<byte[]> renderByName(String name) throws Exception {
        var body = MAPPER.createObjectNode().put("outputType", "pdf");
        body.set("data", MAPPER.readTree(DATA));
        return http.send(HttpRequest.newBuilder(base.resolve("/api/render/" + name))
                .header("Content-Type", "application/json")
                .header("Accept", "application/pdf")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String pdfText(byte[] pdf) throws Exception {
        return extractPdfText(pdf).replaceAll("\\s+", " ");
    }

    private byte[] resource(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/" + name)) {
            return in.readAllBytes();
        }
    }

    private byte[] linkTo(String href) throws Exception {
        return replaceText(resource("vorlage-mit-baustein.odt"), LINK, href);
    }

    /** Ersetzt Text in content.xml; mimetype bleibt erster, unkomprimierter Eintrag. */
    private static byte[] replaceText(byte[] odt, String from, String to) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var zin = new ZipInputStream(new ByteArrayInputStream(odt)); var zout = new ZipOutputStream(out)) {
            for (ZipEntry e = zin.getNextEntry(); e != null; e = zin.getNextEntry()) {
                byte[] data = zin.readAllBytes();
                if (e.getName().equals("content.xml")) {
                    String xml = new String(data, StandardCharsets.UTF_8);
                    assertTrue(xml.contains(from), "nicht gefunden: " + from);
                    data = xml.replace(from, to).getBytes(StandardCharsets.UTF_8);
                }
                ZipEntry copy = new ZipEntry(e.getName());
                if (e.getName().equals("mimetype")) {
                    copy.setMethod(ZipEntry.STORED);
                    copy.setSize(data.length);
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    copy.setCrc(crc.getValue());
                }
                zout.putNextEntry(copy);
                zout.write(data);
                zout.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
