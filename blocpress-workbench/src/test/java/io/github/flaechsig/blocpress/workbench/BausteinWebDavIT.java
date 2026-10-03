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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bausteine (US-0013) und WebDAV-Zugriff aus LibreOffice (US-0014): Bausteine laufen durch denselben
 * Workflow wie Vorlagen, getrennt nach Typ; Entwuerfe sind per WebDAV les- und schreibbar, freigegebene
 * Staende nur lesbar. Ein per WebDAV gespeicherter Entwurf wird wie ein Upload validiert.
 */
@QuarkusTest
@QuarkusTestResource(value = RecordingRenderServerResource.class, restrictToAnnotatedClass = true)
class BausteinWebDavIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE = "/api/workbench/templates/";
    private static final Path CORE = Path.of("../blocpress-core/src/test/resources");

    @BeforeEach
    void reset() {
        RecordingRenderServerResource.reset();
    }

    @Test
    void bausteineAreSeparatedByTypeAndFollowTheApprovalWorkflow() throws Exception {
        String name = "agb-" + UUID.randomUUID();
        String id = upload(name, "BAUSTEIN", Files.readAllBytes(CORE.resolve("special_agreement.odt")));

        assertTrue(names("BAUSTEIN").contains(name), "Baustein fehlt im Reiter Bausteine");
        assertFalse(names("TEMPLATE").contains(name), "Baustein darf nicht bei den Vorlagen erscheinen");

        assertEquals(200, RestAssured.post(BASE + id + "/submit").statusCode());
        Response approve = RestAssured.given().contentType("application/json").body("{\"newStatus\":\"APPROVED\"}")
                .put(BASE + id + "/status");
        assertEquals(200, approve.statusCode(), approve.asString());
        assertEquals(1, RecordingRenderServerResource.calls("POST", "/render/templates/import").size(),
                "freigegebener Baustein muss nach production");
    }

    @Test
    void draftsAreReadWriteAndReleasedVersionsReadOnly() throws Exception {
        String name = "webdav-" + UUID.randomUUID();
        byte[] original = Files.readAllBytes(CORE.resolve("special_agreement.odt"));
        byte[] changed = Files.readAllBytes(CORE.resolve("header_footer.odt"));
        String id = upload(name, "BAUSTEIN", original);
        String draftPath = "/api/webdav/bausteine/" + name + ".odt";

        assertArrayEquals(original, RestAssured.get(draftPath).asByteArray());
        assertEquals(204, RestAssured.given().body(changed).put(draftPath).statusCode());
        assertArrayEquals(changed, RestAssured.get(draftPath).asByteArray(), "WebDAV-PUT nicht gespeichert");

        Response listing = RestAssured.request("PROPFIND", "/api/webdav/bausteine/");
        assertEquals(207, listing.statusCode(), listing.asString());
        assertTrue(listing.asString().contains(name + ".odt"), listing.asString());

        assertEquals(200, RestAssured.post(BASE + id + "/submit").statusCode());
        assertEquals(200, RestAssured.given().contentType("application/json").body("{\"newStatus\":\"APPROVED\"}")
                .put(BASE + id + "/status").statusCode());

        String releasedPath = "/api/webdav/released/bausteine/" + name + ".odt";
        assertArrayEquals(changed, RestAssured.get(releasedPath).asByteArray());
        assertEquals(403, RestAssured.given().body(original).put(releasedPath).statusCode(),
                "freigegebene Staende muessen schreibgeschuetzt sein");
    }

    @Test
    void webDavSaveRevalidatesTheDraft() throws Exception {
        // Neuer Entwurf per WebDAV (create-or-update) — muss validiert und damit einreichbar sein
        String name = "webdav-new-" + UUID.randomUUID();
        Response created = RestAssured.given().body(Files.readAllBytes(CORE.resolve("header_footer.odt")))
                .put("/api/webdav/templates/" + name + ".odt");
        assertEquals(201, created.statusCode(), created.asString());
        String id = idByName(name, "TEMPLATE");

        JsonNode fields = MAPPER.readTree(RestAssured.get(BASE + id + "/details").asString())
                .path("validationResult").path("schema").path("properties");
        assertTrue(fields.has("offer"), "per WebDAV angelegter Entwurf wurde nicht validiert: " + fields);
        assertEquals(200, RestAssured.post(BASE + id + "/submit").statusCode(),
                "per WebDAV angelegter Entwurf muss einreichbar sein");
    }

    private static String upload(String name, String type, byte[] content) throws Exception {
        Response upload = RestAssured.given()
                .multiPart("name", name)
                .multiPart("type", type)
                .multiPart("file", name + ".odt", content, "application/vnd.oasis.opendocument.text")
                .post("/api/workbench/templates");
        assertEquals(201, upload.statusCode(), upload.asString());
        return MAPPER.readTree(upload.asString()).path("id").asText();
    }

    private static java.util.List<String> names(String type) throws Exception {
        var out = new java.util.ArrayList<String>();
        MAPPER.readTree(RestAssured.get("/api/workbench/templates?type=" + type).asString())
                .forEach(t -> out.add(t.path("name").asText()));
        return out;
    }

    private static String idByName(String name, String type) throws Exception {
        for (JsonNode t : MAPPER.readTree(RestAssured.get("/api/workbench/templates?type=" + type).asString())) {
            if (t.path("name").asText().equals(name)) {
                return t.path("id").asText();
            }
        }
        throw new AssertionError("nicht gefunden: " + name);
    }
}
