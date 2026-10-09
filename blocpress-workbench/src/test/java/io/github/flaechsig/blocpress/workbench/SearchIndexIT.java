package io.github.flaechsig.blocpress.workbench;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * REQ-0030, REQ-0032: Der Suchindex folgt jeder Änderung einer Vorlage und baut sich neu auf,
 * wenn er fehlt. Gegen ein echtes Elasticsearch ({@link ElasticsearchTestResource}) und eine
 * Attrappe von render ({@link MockRenderServerResource}).
 */
@QuarkusTest
@QuarkusTestResource(value = ElasticsearchTestResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = MockRenderServerResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = SearchIndexIT.FastIndexCheck.class, restrictToAnnotatedClass = true)
class SearchIndexIT {

    private static final Path CORE = Path.of("../blocpress-core/src/test/resources");

    @Test
    @DisplayName("REQ-0030: replacedContentIsSearchable")
    void replacedContentIsSearchable() throws Exception {
        String id = upload("Austausch-Vorlage", Files.readAllBytes(CORE.resolve("sample-04.odt")));

        RestAssured.given().multiPart("file", CORE.resolve("header_footer.odt").toFile(),
                        "application/vnd.oasis.opendocument.text")
                .put("/api/workbench/templates/" + id + "/content")
                .then().statusCode(200);

        // KOPF-STD steht nur in der Kopfzeile der neuen Datei (REQ-0031)
        awaitHit("KOPF-STD", id, hit -> true);
    }

    @Test
    @DisplayName("REQ-0030: copyAndNewDraftAreSearchable")
    void copyAndNewDraftAreSearchable() throws Exception {
        String id = upload("Kopierquelle", Files.readAllBytes(CORE.resolve("sample-04.odt")));

        String copyId = RestAssured.given().contentType(ContentType.JSON).body(Map.of("name", "Kopieziel"))
                .post("/api/workbench/templates/" + id + "/duplicate")
                .then().statusCode(201).extract().path("id");
        // neben einem Entwurf entsteht kein zweiter (REQ-0042): Quelle als freigegeben markieren
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() ->
                io.github.flaechsig.blocpress.workbench.entity.Template.<io.github.flaechsig.blocpress.workbench.entity.Template>findById(
                        java.util.UUID.fromString(id)).status = io.github.flaechsig.blocpress.workbench.entity.TemplateStatus.APPROVED);
        String draftId = RestAssured.given()
                .post("/api/workbench/templates/" + id + "/new-draft")
                .then().statusCode(201).extract().path("id");

        awaitHit("Kopieziel", copyId, hit -> true);
        awaitHit("Kopierquelle", draftId, hit -> Integer.valueOf(2).equals(hit.get("version")));
    }

    @Test
    @DisplayName("REQ-0030: statusChangesAreSearchableAndRetiredStaysFindable")
    void statusChangesAreSearchableAndRetiredStaysFindable() throws Exception {
        String id = upload("Statuswechsel", Files.readAllBytes(CORE.resolve("sample-04.odt")));

        RestAssured.given().post("/api/workbench/templates/" + id + "/submit").then().statusCode(200);
        awaitHit("Statuswechsel", id, status("SUBMITTED"));

        String afterReject = RestAssured.given().contentType(ContentType.JSON).body(Map.of("reason", "Tippfehler"))
                .post("/api/workbench/templates/" + id + "/reject")
                .then().statusCode(200).extract().path("status");
        awaitHit("Statuswechsel", id, status(afterReject));

        RestAssured.given().post("/api/workbench/templates/" + id + "/submit").then().statusCode(200);
        MockRenderServerResource.respondWithPdf();
        setStatus(id, "APPROVED").then().statusCode(200);
        awaitHit("Statuswechsel", id, status("APPROVED"));

        setStatus(id, "RETIRED").then().statusCode(200);
        awaitHit("Statuswechsel", id, status("RETIRED"));
    }

    @Test
    @DisplayName("REQ-0030: failedApprovalLeavesIndexUnchanged")
    void failedApprovalLeavesIndexUnchanged() throws Exception {
        String id = upload("Gescheiterte-Freigabe", Files.readAllBytes(CORE.resolve("sample-04.odt")));
        RestAssured.given().post("/api/workbench/templates/" + id + "/submit").then().statusCode(200);
        awaitHit("Gescheiterte-Freigabe", id, status("SUBMITTED"));

        MockRenderServerResource.respondWithServerError("render nicht erreichbar");
        setStatus(id, "APPROVED").then().statusCode(503);

        Thread.sleep(2000); // Zeit, in der ein falsch geschriebener Status sichtbar würde
        awaitHit("Gescheiterte-Freigabe", id, status("SUBMITTED"));
    }

    @Test
    @DisplayName("REQ-0030: unreadableFileIsFoundByName")
    void unreadableFileIsFoundByName() throws Exception {
        String id = upload("Kaputte-Datei", "kein ODT".getBytes());

        awaitHit("Kaputte-Datei", id, status("DRAFT"));
    }

    @Test
    @DisplayName("REQ-0032: missingIndexIsRebuiltFromDatabase")
    void missingIndexIsRebuiltFromDatabase() throws Exception {
        String id = upload("Neuaufbau-Vorlage", Files.readAllBytes(CORE.resolve("sample-04.odt")));
        awaitHit("Neuaufbau-Vorlage", id, hit -> true);

        // Elasticsearch-Daten verloren: Index löschen; die regelmäßige Prüfung baut ihn neu auf
        String es = ConfigProvider.getConfig().getValue("quarkus.elasticsearch.hosts", String.class);
        HttpResponse<String> deleted = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://" + es + "/blocpress-templates")).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, deleted.statusCode(), deleted.body());

        awaitHit("Neuaufbau-Vorlage", id, hit -> true);
    }

    @Test
    @DisplayName("REQ-0032: reindexEndpointIndexesAllTemplates")
    void reindexEndpointIndexesAllTemplates() throws Exception {
        String id = upload("Manueller-Neuaufbau", Files.readAllBytes(CORE.resolve("sample-04.odt")));
        long inDatabase = RestAssured.given().get("/api/workbench/templates").jsonPath().getList("$").size();

        int indexed = RestAssured.given().post("/api/workbench/search/reindex")
                .then().statusCode(200).extract().path("indexed");

        assertTrue(indexed >= inDatabase, "indexed=" + indexed + ", in der Liste=" + inDatabase);
        awaitHit("Manueller-Neuaufbau", id, hit -> true);
    }

    private static Response setStatus(String id, String status) {
        return RestAssured.given().contentType(ContentType.JSON).body(Map.of("newStatus", status))
                .put("/api/workbench/templates/" + id + "/status");
    }

    private static Predicate<Map<String, Object>> status(String status) {
        return hit -> status.equals(hit.get("status"));
    }

    private static String upload(String name, byte[] content) throws Exception {
        Path tmp = Files.createTempFile("search-index-it-", ".odt");
        Files.write(tmp, content);
        try {
            String id = RestAssured.given()
                    .multiPart("name", name)
                    .multiPart("file", tmp.toFile(), "application/vnd.oasis.opendocument.text")
                    .post("/api/workbench/templates")
                    .then().statusCode(201).extract().path("id");
            assertNotNull(id);
            return id;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Wartet bis zu 20 s, bis die Suche nach {@code query} den Treffer {@code id} mit der Eigenschaft liefert. */
    private static void awaitHit(String query, String id, Predicate<Map<String, Object>> expected) throws Exception {
        String last = "";
        for (int i = 0; i < 40; i++) {
            Response response = RestAssured.given().queryParam("q", query).get("/api/workbench/search");
            last = response.asString();
            List<Map<String, Object>> hits = response.jsonPath().getList("hits");
            if (hits != null && hits.stream().anyMatch(h -> id.equals(h.get("id")) && expected.test(h))) {
                return;
            }
            Thread.sleep(500);
        }
        fail("Kein passender Treffer für '" + query + "' (" + id + "): " + last);
    }

    /** Prüft den Index alle 2 s statt alle 60 s, damit der Neuaufbau im Test schnell greift. */
    public static class FastIndexCheck implements QuarkusTestResourceLifecycleManager {
        @Override
        public Map<String, String> start() {
            return Map.of("blocpress.search.index-check", "2s", "blocpress.search.index-check-delay", "1s");
        }

        @Override
        public void stop() {
        }
    }
}
