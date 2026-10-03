package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Freigabe-Workflow Ende-zu-Ende gegen einen mitschreibenden Render-Mock:
 * einreichen, ablehnen (US-0016), freigeben mit Auto-Deploy und 503-Pfad (US-0017),
 * Gueltigkeit, Ausmustern und faellige Reviews (US-0019).
 */
@QuarkusTest
@QuarkusTestResource(value = RecordingRenderServerResource.class, restrictToAnnotatedClass = true)
class ApprovalWorkflowIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path TEMPLATE = Path.of("../docs/samples/quickstart/invoice.odt");
    private static final String BASE = "/api/workbench/templates/";

    @BeforeEach
    void reset() {
        RecordingRenderServerResource.reset();
    }

    @Test
    void approvalDeploysTemplateToProduction() throws Exception {
        byte[] odt = Files.readAllBytes(TEMPLATE);
        String id = uploadAndSubmit(odt);

        Response approve = status(id, "{\"newStatus\":\"APPROVED\",\"reviewCycleYears\":3}");
        assertEquals(200, approve.statusCode(), approve.asString());

        var deploys = RecordingRenderServerResource.calls("POST", "/render/templates/import");
        assertEquals(1, deploys.size(), "genau ein Deploy erwartet: " + RecordingRenderServerResource.CALLS);
        JsonNode deployed = MAPPER.readTree(deploys.getFirst().body());
        assertEquals(id, deployed.path("id").asText());
        assertArrayEquals(odt, Base64.getDecoder().decode(deployed.path("contentBase64").asText()),
                "deployter Inhalt weicht vom hochgeladenen ab");
        assertFalse(deployed.path("validFrom").asText().isBlank());

        JsonNode details = details(id);
        assertEquals("APPROVED", details.path("status").asText());
        // validUntil = validFrom + reviewCycleYears (TF-4)
        assertEquals(LocalDate.parse(details.path("validFrom").asText().substring(0, 10)).plusYears(3),
                LocalDate.parse(details.path("validUntil").asText().substring(0, 10)));
        assertEquals(deployed.path("validUntil").asText().substring(0, 10),
                details.path("validUntil").asText().substring(0, 10));
    }

    @Test
    void approvalFailsWith503AndTemplateStaysSubmittedWhenRenderIsDown() throws Exception {
        String id = uploadAndSubmit(Files.readAllBytes(TEMPLATE));
        RecordingRenderServerResource.status = 500;

        Response approve = status(id, "{\"newStatus\":\"APPROVED\"}");

        assertEquals(503, approve.statusCode(), approve.asString());
        assertEquals("SUBMITTED", details(id).path("status").asText(),
                "fehlgeschlagener Deploy darf die Freigabe nicht durchgehen lassen");
    }

    @Test
    void rejectionReturnsToDraftWithReason() throws Exception {
        String id = uploadAndSubmit(Files.readAllBytes(TEMPLATE));

        Response reject = RestAssured.given().contentType("application/json")
                .body("{\"reason\":\"Anrede fehlt\"}").post(BASE + id + "/reject");
        assertEquals(200, reject.statusCode(), reject.asString());

        JsonNode details = details(id);
        assertEquals("DRAFT", details.path("status").asText());
        assertEquals("Anrede fehlt", details.path("rejectionReason").asText());
        assertTrue(RecordingRenderServerResource.calls("POST", "/render/templates/import").isEmpty(),
                "Ablehnung darf nichts deployen");
    }

    @Test
    void retiringRemovesTemplateFromProduction() throws Exception {
        String id = uploadAndSubmit(Files.readAllBytes(TEMPLATE));
        assertEquals(200, status(id, "{\"newStatus\":\"APPROVED\"}").statusCode());
        String name = details(id).path("name").asText();

        assertEquals(200, status(id, "{\"newStatus\":\"RETIRED\"}").statusCode());

        assertEquals(1, RecordingRenderServerResource.calls("DELETE", "/render/templates/import/" + name).size(),
                "RETIRED muss die Vorlage aus production entfernen: " + RecordingRenderServerResource.CALLS);
        assertEquals("RETIRED", details(id).path("status").asText());
    }

    @Test
    void templatesExpiringWithinLeadDaysAreDueForReview() throws Exception {
        // validUntil = validFrom + 1 Jahr = heute + 10 Tage -> faellig (Vorlauf 60 Tage)
        String due = uploadAndSubmit(Files.readAllBytes(TEMPLATE));
        LocalDate from = LocalDate.now().minusYears(1).plusDays(10);
        assertEquals(200, status(due, "{\"newStatus\":\"APPROVED\",\"validFrom\":\"" + from + "\",\"reviewCycleYears\":1}").statusCode());
        // validUntil in 5 Jahren -> nicht faellig
        String notDue = uploadAndSubmit(Files.readAllBytes(TEMPLATE));
        assertEquals(200, status(notDue, "{\"newStatus\":\"APPROVED\",\"reviewCycleYears\":5}").statusCode());

        JsonNode list = MAPPER.readTree(RestAssured.get(BASE + "due-for-review").asString());
        var ids = new java.util.HashSet<String>();
        list.forEach(t -> ids.add(t.path("id").asText()));
        assertTrue(ids.contains(due), "faellige Vorlage fehlt: " + list);
        assertFalse(ids.contains(notDue), "nicht faellige Vorlage gelistet: " + list);
    }

    @Test
    void reuploadUnderSameNameCreatesNextVersionAndDeploysIt() throws Exception {
        byte[] odt = Files.readAllBytes(TEMPLATE);
        String name = "versioniert-" + UUID.randomUUID();
        JsonNode v1 = MAPPER.readTree(upload(name, odt).asString());
        JsonNode v2 = MAPPER.readTree(upload(name, odt).asString());
        assertEquals(1, v1.path("version").asInt());
        assertEquals(2, v2.path("version").asInt(), "erneuter Upload muss Version 2 ergeben");

        String id = v2.path("id").asText();
        assertEquals(200, RestAssured.post(BASE + id + "/submit").statusCode());
        assertEquals(200, status(id, "{\"newStatus\":\"APPROVED\"}").statusCode());
        JsonNode deployed = MAPPER.readTree(RecordingRenderServerResource.calls("POST", "/render/templates/import")
                .getLast().body());
        assertEquals(2, deployed.path("version").asInt(), "deployte Version");
        assertEquals(name, deployed.path("name").asText());
    }

    @Test
    void byNameDeliversLatestActiveApprovedVersion() throws Exception {
        byte[] odt = Files.readAllBytes(TEMPLATE);
        String name = "byname-" + UUID.randomUUID();
        String v1 = MAPPER.readTree(upload(name, odt).asString()).path("id").asText();
        assertEquals(404, RestAssured.get(BASE + "by-name/" + name + "/content").statusCode(), "nur Entwurf → 404");

        assertEquals(200, RestAssured.post(BASE + v1 + "/submit").statusCode());
        assertEquals(200, status(v1, "{\"newStatus\":\"APPROVED\"}").statusCode());
        String v2 = MAPPER.readTree(upload(name, odt).asString()).path("id").asText();
        assertEquals("1", RestAssured.get(BASE + "by-name/" + name + "/content").header("X-Template-Version"),
                "Entwurf v2 darf v1 nicht verdraengen");

        assertEquals(200, RestAssured.post(BASE + v2 + "/submit").statusCode());
        assertEquals(200, status(v2, "{\"newStatus\":\"APPROVED\"}").statusCode());
        assertEquals("2", RestAssured.get(BASE + "by-name/" + name + "/content").header("X-Template-Version"));

        assertEquals(404, RestAssured.get(BASE + "by-name/gibt-es-nicht-" + UUID.randomUUID() + "/content").statusCode());
    }

    @Test
    void byNameDoesNotDeliverExpiredVersion() throws Exception {
        // freigegeben vor 2 Jahren mit Review-Zyklus 1 Jahr → seit einem Jahr abgelaufen (render sperrt mit 404)
        String name = "abgelaufen-" + UUID.randomUUID();
        String id = MAPPER.readTree(upload(name, Files.readAllBytes(TEMPLATE)).asString()).path("id").asText();
        assertEquals(200, RestAssured.post(BASE + id + "/submit").statusCode());
        assertEquals(200, status(id, "{\"newStatus\":\"APPROVED\",\"validFrom\":\"" + LocalDate.now().minusYears(2)
                + "\",\"reviewCycleYears\":1}").statusCode());

        assertEquals(404, RestAssured.get(BASE + "by-name/" + name + "/content").statusCode(),
                "abgelaufene Version darf nicht als aktiv ausgeliefert werden (wie in render)");
    }

    private static Response upload(String name, byte[] odt) {
        Response upload = RestAssured.given()
                .multiPart("name", name)
                .multiPart("file", "template.odt", odt, "application/vnd.oasis.opendocument.text")
                .post("/api/workbench/templates");
        assertEquals(201, upload.statusCode(), upload.asString());
        return upload;
    }

    private String uploadAndSubmit(byte[] odt) throws Exception {
        String id = MAPPER.readTree(upload("freigabe-" + UUID.randomUUID(), odt).asString()).path("id").asText();

        Response submit = RestAssured.post(BASE + id + "/submit");
        assertEquals(200, submit.statusCode(), "Einreichen fehlgeschlagen: " + submit.asString());
        return id;
    }

    private static Response status(String id, String json) {
        return RestAssured.given().contentType("application/json").body(json).put(BASE + id + "/status");
    }

    private static JsonNode details(String id) throws Exception {
        return MAPPER.readTree(RestAssured.get(BASE + id + "/details").asString());
    }
}
