package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressionstest einer Vorlage (US-0018): erwartetes PDF speichern, Lauf gegen das aktuell
 * gerenderte PDF, bekannte Abweichung per Ignorier-Muster akzeptieren, Diff-PDF erzeugen.
 * Das "aktuelle" PDF liefert der Render-Mock; die PDFs werden mit PDFBox erzeugt.
 * Benoetigt pdftohtml (poppler-utils) — in den Images und in der CI installiert.
 */
@QuarkusTest
@QuarkusTestResource(value = RecordingRenderServerResource.class, restrictToAnnotatedClass = true)
class RegressionRunIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE = "/api/workbench/templates/";

    private static final byte[] EXPECTED = pdf("Rechnung LT-1", "Betrag 100,00 EUR", "Datum 01.01.2026");
    private static final byte[] AMOUNT_CHANGED = pdf("Rechnung LT-1", "Betrag 200,00 EUR", "Datum 01.01.2026");
    private static final byte[] DATE_CHANGED = pdf("Rechnung LT-1", "Betrag 100,00 EUR", "Datum 03.10.2026");

    private String templateId;
    private String testDataId;

    @BeforeEach
    void setUp() throws Exception {
        RecordingRenderServerResource.reset();
        Response upload = RestAssured.given()
                .multiPart("name", "regression-" + UUID.randomUUID())
                .multiPart("file", "template.odt", Files.readAllBytes(Path.of("../site/samples/quickstart/invoice.odt")),
                        "application/vnd.oasis.opendocument.text")
                .post("/api/workbench/templates");
        assertEquals(201, upload.statusCode(), upload.asString());
        templateId = MAPPER.readTree(upload.asString()).path("id").asText();

        Response tds = RestAssured.given().contentType("application/json")
                .body("{\"name\":\"Standardfall\",\"testData\":{\"invoice\":{\"number\":\"LT-1\"}}}")
                .post(BASE + templateId + "/testdata");
        assertEquals(201, tds.statusCode(), tds.asString());
        testDataId = MAPPER.readTree(tds.asString()).path("id").asText();
    }

    @Test
    void withoutExpectedPdfTheRunReportsNoBaseline() throws Exception {
        JsonNode result = run();
        assertFalse(result.path("hasExpectedPdf").asBoolean(), result.toString());
        assertFalse(result.path("passed").asBoolean(), result.toString());
    }

    @Test
    void identicalRenderingPasses() throws Exception {
        saveExpected(EXPECTED);
        RecordingRenderServerResource.renderResponse = EXPECTED;

        JsonNode result = run();
        assertTrue(result.path("passed").asBoolean(), result.toString());
        assertFalse(result.path("hasAcceptedDeviations").asBoolean(), result.toString());
    }

    @Test
    void changedContentFails() throws Exception {
        saveExpected(EXPECTED);
        RecordingRenderServerResource.renderResponse = AMOUNT_CHANGED;

        JsonNode result = run();
        assertTrue(result.path("hasExpectedPdf").asBoolean());
        assertFalse(result.path("passed").asBoolean(), "geaenderter Betrag muss auffallen: " + result);
    }

    @Test
    void ignoredPatternTurnsKnownDeviationIntoAcceptedDeviation() throws Exception {
        saveExpected(EXPECTED);
        RecordingRenderServerResource.renderResponse = DATE_CHANGED;
        assertFalse(run().path("passed").asBoolean(), "ohne Muster muss das Datum auffallen");

        Response ignore = RestAssured.given().contentType("application/json")
                .body("{\"patterns\":[\"Datum \\\\d{2}\\\\.\\\\d{2}\\\\.\\\\d{4}\"]}")
                .put(BASE + templateId + "/ignored-patterns");
        assertEquals(200, ignore.statusCode(), ignore.asString());

        JsonNode result = run();
        assertTrue(result.path("passed").asBoolean(), result.toString());
        assertTrue(result.path("hasAcceptedDeviations").asBoolean(), "Abweichung muss als akzeptiert markiert sein: " + result);

        // ein Muster ueberdeckt nicht beliebige Aenderungen
        RecordingRenderServerResource.renderResponse = AMOUNT_CHANGED;
        assertFalse(run().path("passed").asBoolean(), "geaenderter Betrag trotz Datums-Muster nicht erkannt");
    }

    @Test
    void runAllAndDiffPdf() throws Exception {
        saveExpected(EXPECTED);
        RecordingRenderServerResource.renderResponse = AMOUNT_CHANGED;

        JsonNode all = MAPPER.readTree(RestAssured.post(BASE + templateId + "/run-all-regressions").asString());
        assertEquals(1, all.size(), all.toString());
        assertFalse(all.get(0).path("passed").asBoolean(), all.toString());

        Response diff = RestAssured.post(BASE + templateId + "/testdata/" + testDataId + "/regression-diff");
        assertEquals(200, diff.statusCode(), diff.asString());
        assertTrue(diff.asString().startsWith("%PDF"), "Diff ist kein PDF");
    }

    private void saveExpected(byte[] pdf) {
        Response r = RestAssured.given().contentType("application/octet-stream").body(pdf)
                .post(BASE + templateId + "/testdata/" + testDataId + "/save-expected");
        assertEquals(200, r.statusCode(), r.asString());
    }

    private JsonNode run() throws Exception {
        Response r = RestAssured.post(BASE + templateId + "/testdata/" + testDataId + "/run-regression");
        assertEquals(200, r.statusCode(), r.asString());
        return MAPPER.readTree(r.asString());
    }

    private static byte[] pdf(String... lines) {
        try (PDDocument doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 700;
                for (String line : lines) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 14);
                    cs.newLineAtOffset(72, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 40;
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
