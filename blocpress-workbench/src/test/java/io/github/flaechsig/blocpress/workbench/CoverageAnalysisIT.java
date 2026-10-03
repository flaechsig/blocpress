package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage-Analyse (US-0012): welche Felder, Bedingungszweige (wahr/falsch) und Wiederholungsfaelle
 * (0/1/2+ Elemente) decken die Testdatensaetze ab — und schliesst ein uebernommener Vorschlag die Luecke?
 */
@QuarkusTest
class CoverageAnalysisIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE = "/api/workbench/templates/";
    private static final Path CORE = Path.of("../blocpress-core/src/test/resources");

    @Test
    void conditionBranchesAreTrackedAndSuggestionClosesTheGap() throws Exception {
        String id = upload("header_footer.odt");
        createTestData(id, "Frau", "{\"kunde\":{\"anrede\":\"FRAU\"},\"numbertest\":5,\"offer\":{\"reference\":\"R\"}}");

        JsonNode coverage = coverage(id);
        JsonNode anrede = conditionContaining(coverage, "kunde.anrede");
        assertTrue(anrede.get(0).asBoolean(), "wahr-Zweig muss abgedeckt sein: " + coverage);
        assertFalse(anrede.get(1).asBoolean(), "falsch-Zweig darf noch nicht abgedeckt sein: " + coverage);
        assertTrue(coverage.path("coveragePercent").asInt() < 100, coverage.toString());

        // Vorschlag fuer den fehlenden Zweig uebernehmen
        JsonNode suggestion = null;
        for (JsonNode s : coverage.path("suggestions")) {
            if (s.path("reason").asText().contains("kunde.anrede")) {
                suggestion = s;
            }
        }
        assertTrue(suggestion != null, "kein Vorschlag fuer den falsch-Zweig: " + coverage.path("suggestions"));
        Response created = RestAssured.given().contentType("application/json").body(suggestion.toString())
                .post(BASE + id + "/testdata/from-suggestion");
        assertEquals(201, created.statusCode(), created.asString());

        JsonNode after = conditionContaining(coverage(id), "kunde.anrede");
        assertTrue(after.get(0).asBoolean() && after.get(1).asBoolean(), "beide Zweige muessen abgedeckt sein: " + after);
    }

    @Test
    void repetitionGroupsTrackZeroOneAndManyElements() throws Exception {
        String id = upload("loop_table.odt");
        createTestData(id, "Drei Produkte", """
                {"kunde":"Max","produkte":[{"name":"A","menge":1,"preis":1.0},{"name":"B","menge":2,"preis":2.0},
                                           {"name":"C","menge":3,"preis":3.0}]}""");

        JsonNode group = coverage(id).path("repetitionGroupCoverage").path("produkte");
        assertEquals("[false,false,true]", group.toString(), "nur der 2+-Fall ist abgedeckt");

        createTestData(id, "Leer", "{\"kunde\":\"Max\",\"produkte\":[]}");
        createTestData(id, "Eins", "{\"kunde\":\"Max\",\"produkte\":[{\"name\":\"A\",\"menge\":1,\"preis\":1.0}]}");
        JsonNode full = coverage(id);
        assertEquals("[true,true,true]", full.path("repetitionGroupCoverage").path("produkte").toString(), full.toString());
    }

    private static JsonNode conditionContaining(JsonNode coverage, String path) {
        var it = coverage.path("conditionCoverage").fields();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getKey().contains(path)) {
                return e.getValue();
            }
        }
        throw new AssertionError("Bedingung mit " + path + " fehlt: " + coverage);
    }

    private static String upload(String template) throws Exception {
        Response upload = RestAssured.given()
                .multiPart("name", "coverage-" + UUID.randomUUID())
                .multiPart("file", template, Files.readAllBytes(CORE.resolve(template)),
                        "application/vnd.oasis.opendocument.text")
                .post("/api/workbench/templates");
        assertEquals(201, upload.statusCode(), upload.asString());
        return MAPPER.readTree(upload.asString()).path("id").asText();
    }

    private static void createTestData(String id, String name, String json) {
        Response r = RestAssured.given().contentType("application/json")
                .body("{\"name\":\"" + name + "\",\"testData\":" + json + "}")
                .post(BASE + id + "/testdata");
        assertEquals(201, r.statusCode(), r.asString());
    }

    private static JsonNode coverage(String id) throws Exception {
        Response r = RestAssured.get(BASE + id + "/coverage");
        assertEquals(200, r.statusCode(), r.asString());
        return MAPPER.readTree(r.asString());
    }
}
