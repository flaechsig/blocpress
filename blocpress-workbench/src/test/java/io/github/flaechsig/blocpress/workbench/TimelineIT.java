package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flaechsig.blocpress.workbench.entity.Template;
import io.github.flaechsig.blocpress.workbench.entity.TemplateStatus;
import io.github.flaechsig.blocpress.workbench.entity.TemplateType;
import io.github.flaechsig.blocpress.workbench.service.TimelineConflictReport;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ein Name, eine Zeitachse (US-0061): kein Rueckdatieren, hoechstens eine gueltige Version,
 * ein Entwurf je Name, eindeutige Namen ueber beide Typen und die Konfliktmeldung beim Start.
 */
@QuarkusTest
@QuarkusTestResource(value = RecordingRenderServerResource.class, restrictToAnnotatedClass = true)
class TimelineIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path ODT = Path.of("../site/samples/quickstart/invoice.odt");
    private static final String BASE = "/api/workbench/templates/";

    @Inject
    TimelineConflictReport report;

    @BeforeEach
    void reset() {
        RecordingRenderServerResource.reset();
    }

    // ===== REQ-0041: kein Rueckdatieren =====

    @Test
    @DisplayName("REQ-0041: approvalWithPastStartIsRejected")
    void approvalWithPastStartIsRejected() throws Exception {
        String id = uploadAndSubmit(name());
        Response r = status(id, "{\"newStatus\":\"APPROVED\",\"validFrom\":\"" + LocalDate.now().minusDays(1) + "\"}");
        assertEquals(400, r.statusCode(), r.asString());
        assertEquals("SUBMITTED", details(id).path("status").asText());
    }

    @Test
    @DisplayName("REQ-0041: todayOrNoDateMeansFromNowAndFutureDateFromStartOfDay")
    void todayOrNoDateMeansFromNowAndFutureDateFromStartOfDay() throws Exception {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        String today = uploadAndSubmit(name());
        assertEquals(200, status(today, "{\"newStatus\":\"APPROVED\",\"validFrom\":\"" + LocalDate.now() + "\"}").statusCode());
        LocalDateTime from = validFrom(today);
        assertFalse(from.isBefore(before), "heute muss 'ab jetzt' heissen, nicht Tagesbeginn: " + from);

        String none = uploadAndSubmit(name());
        assertEquals(200, status(none, "{\"newStatus\":\"APPROVED\"}").statusCode());
        assertFalse(validFrom(none).isBefore(before), "ohne Datum ab jetzt, nicht ab Anlage: " + validFrom(none));

        String future = uploadAndSubmit(name());
        LocalDate in10 = LocalDate.now().plusDays(10);
        assertEquals(200, status(future, "{\"newStatus\":\"APPROVED\",\"validFrom\":\"" + in10 + "\"}").statusCode());
        assertEquals(in10.atStartOfDay(), validFrom(future));
    }

    // ===== REQ-0040 / REQ-0064: hoechstens eine gueltige Version =====

    @Test
    @DisplayName("REQ-0040: approvalEndsPreviousVersionAtStartOfNewOne")
    void approvalEndsPreviousVersionAtStartOfNewOne() throws Exception {
        String name = name();
        String v1 = uploadAndSubmit(name);
        assertEquals(200, status(v1, "{\"newStatus\":\"APPROVED\"}").statusCode());
        String v2 = uploadAndSubmit(name);
        LocalDate in10 = LocalDate.now().plusDays(10);
        assertEquals(200, status(v2, "{\"newStatus\":\"APPROVED\",\"validFrom\":\"" + in10 + "\"}").statusCode());

        assertEquals(in10.atStartOfDay(), validUntil(v1), "v1 muss am Beginn von v2 enden");
        assertEquals(in10.atStartOfDay(), validFrom(v2));
    }

    @Test
    @DisplayName("REQ-0064: approvalBeforeLaterApprovedVersionIsRejected")
    void approvalBeforeLaterApprovedVersionIsRejected() throws Exception {
        String name = name();
        String v1 = uploadAndSubmit(name);
        assertEquals(200, status(v1, "{\"newStatus\":\"APPROVED\",\"validFrom\":\"" + LocalDate.now().plusDays(30) + "\"}").statusCode());
        String v2 = uploadAndSubmit(name);

        Response r = status(v2, "{\"newStatus\":\"APPROVED\",\"validFrom\":\"" + LocalDate.now().plusDays(10) + "\"}");

        assertEquals(409, r.statusCode(), r.asString());
        assertEquals("SUBMITTED", details(v2).path("status").asText());
        assertNull(validUntil(v1), "die kuenftige Version darf nicht veraendert werden");
    }

    // ===== REQ-0042: ein Entwurf je Name =====

    @Test
    @DisplayName("REQ-0042: noSecondDraftNextToADraftOrSubmittedVersion")
    void noSecondDraftNextToADraftOrSubmittedVersion() throws Exception {
        String name = name();
        String draft = id(upload(name, "TEMPLATE"));
        assertEquals(409, upload(name, "TEMPLATE").statusCode(), "zweiter Upload neben Entwurf");
        assertEquals(409, duplicate(draft, name).statusCode(), "Kopie gleichen Namens neben Entwurf");
        assertEquals(409, RestAssured.post(BASE + draft + "/new-draft").statusCode(), "neuer Entwurf neben Entwurf");

        assertEquals(200, RestAssured.post(BASE + draft + "/submit").statusCode());
        assertEquals(409, upload(name, "TEMPLATE").statusCode(), "eingereichte Version zaehlt als Entwurf");

        assertEquals(200, status(draft, "{\"newStatus\":\"APPROVED\"}").statusCode());
        assertEquals(201, upload(name, "TEMPLATE").statusCode(), "nach der Freigabe darf ein neuer Entwurf entstehen");
    }

    // ===== REQ-0039: ein Name ueber beide Typen =====

    @Test
    @DisplayName("REQ-0039: nameOfOneTypeCannotBeUsedByTheOther")
    void nameOfOneTypeCannotBeUsedByTheOther() throws Exception {
        String name = name();
        String template = id(upload(name, "TEMPLATE"));
        assertEquals(200, RestAssured.post(BASE + template + "/submit").statusCode());
        assertEquals(200, status(template, "{\"newStatus\":\"APPROVED\"}").statusCode());

        assertEquals(409, upload(name, "BAUSTEIN").statusCode(), "Upload als Baustein");
        assertEquals(409, RestAssured.given().contentType("application/vnd.oasis.opendocument.text")
                .body(Files.readAllBytes(ODT)).put("/api/webdav/bausteine/" + name + ".odt").statusCode(), "WebDAV-PUT als Baustein");
    }

    @Test
    @DisplayName("REQ-0039: copyUnderSameNameKeepsTheType")
    void copyUnderSameNameKeepsTheType() throws Exception {
        String name = name();
        String baustein = id(upload(name, "BAUSTEIN"));
        assertEquals(200, RestAssured.post(BASE + baustein + "/submit").statusCode());
        assertEquals(200, status(baustein, "{\"newStatus\":\"APPROVED\"}").statusCode());

        Response copy = duplicate(baustein, name);
        assertEquals(201, copy.statusCode(), copy.asString());
        String copyId = id(copy);
        assertEquals(TemplateType.BAUSTEIN, QuarkusTransaction.requiringNew().call(
                () -> Template.<Template>findById(UUID.fromString(copyId)).type));
    }

    // ===== REQ-0043: Konflikte melden, nichts aendern =====

    @Test
    @DisplayName("REQ-0043: conflictsInExistingDataAreReportedAndLeftUnchanged")
    void conflictsInExistingDataAreReportedAndLeftUnchanged() {
        String both = name(), drafts = name(), overlap = name();
        LocalDateTime now = LocalDateTime.now();
        QuarkusTransaction.requiringNew().run(() -> {
            persist(both, 1, TemplateType.TEMPLATE, TemplateStatus.APPROVED, now.minusDays(5), null);
            persist(both, 1, TemplateType.BAUSTEIN, TemplateStatus.DRAFT, null, null);
            persist(drafts, 1, TemplateType.TEMPLATE, TemplateStatus.DRAFT, null, null);
            persist(drafts, 2, TemplateType.TEMPLATE, TemplateStatus.SUBMITTED, null, null);
            persist(overlap, 1, TemplateType.TEMPLATE, TemplateStatus.APPROVED, now.minusDays(10), null);
            persist(overlap, 2, TemplateType.TEMPLATE, TemplateStatus.APPROVED, now.minusDays(3), null);
        });
        long before = QuarkusTransaction.requiringNew().call(() -> Template.count());

        List<String> conflicts = report.report();

        assertTrue(conflicts.stream().anyMatch(c -> c.contains(both) && c.contains("Vorlage und Baustein")), conflicts.toString());
        assertTrue(conflicts.stream().anyMatch(c -> c.contains(drafts) && c.contains("mehr als einen Entwurf")), conflicts.toString());
        assertTrue(conflicts.stream().anyMatch(c -> c.contains(overlap) && c.contains("gleichzeitig")), conflicts.toString());
        assertEquals(before, QuarkusTransaction.requiringNew().call(() -> Template.count()), "die Meldung darf nichts aendern");
        assertNull(QuarkusTransaction.requiringNew().call(() -> Template.<Template>find(
                "name = ?1 AND version = 1", overlap).firstResult().validUntil), "Ueberlappung darf nicht aufgeloest werden");

        // aufraeumen, damit andere Tests nicht ueber diese Konflikte stolpern
        QuarkusTransaction.requiringNew().run(() -> Template.delete("name IN ?1", List.of(both, drafts, overlap)));
    }

    // ===== Hilfen =====

    private static void persist(String name, int version, TemplateType type, TemplateStatus status,
                                LocalDateTime from, LocalDateTime until) {
        Template t = new Template();
        t.name = name;
        t.version = version;
        t.type = type;
        t.status = status;
        t.content = new byte[]{1};
        t.createdAt = LocalDateTime.now();
        if (from != null) {
            t.validFrom = from;   // sonst der Anlagezeitpunkt (Pflichtfeld)
        }
        t.validUntil = until;
        t.persist();
    }

    private static String name() {
        return "zeitachse-" + UUID.randomUUID();
    }

    private static Response upload(String name, String type) throws Exception {
        return RestAssured.given()
                .multiPart("name", name)
                .multiPart("type", type)
                .multiPart("file", "template.odt", Files.readAllBytes(ODT), "application/vnd.oasis.opendocument.text")
                .post("/api/workbench/templates");
    }

    private static String uploadAndSubmit(String name) throws Exception {
        Response upload = upload(name, "TEMPLATE");
        assertEquals(201, upload.statusCode(), upload.asString());
        String id = id(upload);
        assertEquals(200, RestAssured.post(BASE + id + "/submit").statusCode());
        return id;
    }

    private static Response duplicate(String id, String name) {
        return RestAssured.given().contentType("application/json").body("{\"name\":\"" + name + "\"}")
                .post(BASE + id + "/duplicate");
    }

    private static Response status(String id, String json) {
        return RestAssured.given().contentType("application/json").body(json).put(BASE + id + "/status");
    }

    private static String id(Response r) throws Exception {
        return MAPPER.readTree(r.asString()).path("id").asText();
    }

    private static JsonNode details(String id) throws Exception {
        return MAPPER.readTree(RestAssured.get(BASE + id + "/details").asString());
    }

    private static LocalDateTime validFrom(String id) {
        return QuarkusTransaction.requiringNew().call(() -> Template.<Template>findById(UUID.fromString(id)).validFrom);
    }

    private static LocalDateTime validUntil(String id) {
        return QuarkusTransaction.requiringNew().call(() -> Template.<Template>findById(UUID.fromString(id)).validUntil);
    }
}
