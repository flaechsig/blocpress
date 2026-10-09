package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flaechsig.blocpress.workbench.entity.Template;
import io.github.flaechsig.blocpress.workbench.entity.TemplateStatus;
import io.github.flaechsig.blocpress.workbench.entity.TemplateType;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vorschau setzt verknuepfte Bausteine selbst ein (ADR-0018, REQ-0045). Der Mock fuer render merkt
 * sich, was die Workbench schickt: eine Vorlage ohne Verknuepfung, mit dem Inhalt des Bausteins.
 */
@QuarkusTest
@QuarkusTestResource(value = MockRenderServerResource.class, restrictToAnnotatedClass = true)
class BuildingBlockPreviewIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LINK = "http://unreachable.invalid/api/webdav/design/bausteine/Sondervereinbarung.odt";
    private static final String PREVIEW_JSON = """
            {"data": {"customer": [{"firstName": "Michael", "lastName": "Miller"}]}, "outputType": "pdf"}
            """;

    @Test
    @DisplayName("REQ-0045: Vorschau setzt den Entwurf ein, sonst die gueltige freigegebene Version")
    void previewInlinesDraftOtherwiseApprovedBuildingBlock() throws Exception {
        String block = "SV-" + UUID.randomUUID();
        persistBuildingBlock(block, 1, TemplateStatus.APPROVED, resource("sondervereinbarung-freigegeben.odt"));
        String templateId = uploadTemplateLinking(block);
        MockRenderServerResource.respondWithPdf();

        assertEquals(200, preview(templateId).statusCode());
        String sent = contentXmlOfSentTemplate();
        assertFalse(sent.contains("text:section-source"), "render darf keine Verknuepfung bekommen");
        assertTrue(sent.contains("Special Agreement with"), "freigegebener Stand erwartet: " + sent);

        persistBuildingBlock(block, 2, TemplateStatus.DRAFT, resource("sondervereinbarung-entwurf.odt"));

        assertEquals(200, preview(templateId).statusCode());
        sent = contentXmlOfSentTemplate();
        assertFalse(sent.contains("text:section-source"));
        assertTrue(sent.contains("Draft Agreement with"), "Entwurf erwartet: " + sent);
        assertFalse(sent.contains("Special Agreement with"), sent);
    }

    @Test
    @DisplayName("REQ-0045: Regressionstest setzt den Baustein ebenso ein")
    void regressionRunInlinesBuildingBlock() throws Exception {
        String block = "SV-" + UUID.randomUUID();
        persistBuildingBlock(block, 1, TemplateStatus.APPROVED, resource("sondervereinbarung-freigegeben.odt"));
        String templateId = uploadTemplateLinking(block);
        MockRenderServerResource.respondWithPdf();

        Response tds = RestAssured.given().contentType(ContentType.JSON)
                .body("{\"name\":\"Standardfall\",\"testData\":{\"customer\":[{\"firstName\":\"Michael\"}]}}")
                .post("/api/workbench/templates/" + templateId + "/testdata");
        assertEquals(201, tds.statusCode(), tds.asString());
        String testDataId = tds.jsonPath().getString("id");
        Response expected = RestAssured.given().contentType("application/octet-stream")
                .body(MockRenderServerResource.responseBody)
                .post("/api/workbench/templates/" + templateId + "/testdata/" + testDataId + "/save-expected");
        assertEquals(200, expected.statusCode(), expected.asString());
        MockRenderServerResource.lastRequestBody = null;

        Response run = RestAssured.post("/api/workbench/templates/" + templateId + "/testdata/" + testDataId + "/run-regression");

        assertEquals(200, run.statusCode(), run.asString());
        String sent = contentXmlOfSentTemplate();
        assertFalse(sent.contains("text:section-source"), "render darf keine Verknuepfung bekommen");
        assertTrue(sent.contains("Special Agreement with"), sent);
    }

    @Test
    @DisplayName("REQ-0045: Vorschau mit unbekanntem Baustein wird abgelehnt, ohne render aufzurufen")
    void previewWithUnknownBuildingBlockIsRejected() throws Exception {
        String templateId = uploadTemplateLinking("fehlt-" + UUID.randomUUID());
        MockRenderServerResource.respondWithPdf();
        MockRenderServerResource.lastRequestBody = null;

        Response response = preview(templateId);

        assertEquals(422, response.statusCode(), response.asString());
        assertEquals(null, MockRenderServerResource.lastRequestBody, "render darf nicht aufgerufen werden");
    }

    private static void persistBuildingBlock(String name, int version, TemplateStatus status, byte[] content) {
        QuarkusTransaction.requiringNew().run(() -> {
            Template t = new Template();
            t.name = name;
            t.version = version;
            t.type = TemplateType.BAUSTEIN;
            t.status = status;
            t.content = content;
            t.createdAt = LocalDateTime.now();
            t.validFrom = LocalDateTime.now().minusDays(1);
            t.persist();
        });
    }

    private String uploadTemplateLinking(String block) throws Exception {
        byte[] odt = replaceText(resource("vorlage-mit-baustein.odt"), LINK,
                "http://unreachable.invalid/api/webdav/design/bausteine/" + block + ".odt");
        Response upload = RestAssured.given()
                .multiPart("name", "T-" + UUID.randomUUID())
                .multiPart("file", "vorlage.odt", odt, "application/vnd.oasis.opendocument.text")
                .post("/api/workbench/templates");
        assertEquals(201, upload.statusCode(), upload.asString());
        return upload.jsonPath().getString("id");
    }

    private static Response preview(String templateId) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body(PREVIEW_JSON)
                .post("/api/workbench/templates/" + templateId + "/preview");
    }

    private static String contentXmlOfSentTemplate() throws Exception {
        var request = MAPPER.readTree(MockRenderServerResource.lastRequestBody);
        byte[] odt = Base64.getDecoder().decode(request.path("template").asText());
        try (var zip = new ZipInputStream(new ByteArrayInputStream(odt))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (e.getName().equals("content.xml")) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("content.xml fehlt");
    }

    private byte[] resource(String name) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            return in.readAllBytes();
        }
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
