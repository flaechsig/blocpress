package io.github.flaechsig.blocpress.render;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.github.flaechsig.blocpress.render.AuthTestClient.devToken;
import static io.github.flaechsig.blocpress.render.AuthTestClient.forgedToken;
import static io.github.flaechsig.blocpress.render.AuthTestClient.tokenWithGroups;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * REQ-0054: Bei eingeschalteter Absicherung nimmt render Import und Entfernen nur mit einem
 * gueltigen Token der Gruppe reviewer an (ADR-0019). Das Dev-Token traegt nur api-consumer.
 */
@QuarkusTest
@TestProfile(RenderAuthEnabledTest.AuthEnabled.class)
class ImportAuthEnabledTest {

    @TestHTTPResource("/")
    URI base;

    @Test
    @DisplayName("REQ-0054: importWithoutValidTokenIsRejectedWith401")
    void importWithoutValidTokenIsRejectedWith401() throws Exception {
        var client = new AuthTestClient(base);
        assertEquals(401, client.importTemplate("Import-ohne-Token", null));
        assertEquals(401, client.importTemplate("Import-gefaelscht", forgedToken()));
        assertEquals(401, client.delete("/api/render/templates/import/Irgendeine", null));
        assertEquals(401, client.delete("/api/render/templates/import/Irgendeine", forgedToken()));
    }

    @Test
    @DisplayName("REQ-0054: importWithoutReviewerGroupIsRejectedWith403")
    void importWithoutReviewerGroupIsRejectedWith403() throws Exception {
        var client = new AuthTestClient(base);
        assertEquals(403, client.importTemplate("Import-ohne-Rolle", devToken()));
        assertEquals(403, client.importTemplate("Import-ohne-Rolle", tokenWithGroups("designer")));
        assertEquals(403, client.delete("/api/render/templates/import/Irgendeine", devToken()));
    }

    @Test
    @DisplayName("REQ-0054: reviewerMayImportAndRemove")
    void reviewerMayImportAndRemove() throws Exception {
        var client = new AuthTestClient(base);
        String reviewer = tokenWithGroups("reviewer");
        assertEquals(200, client.importTemplate("Import-Reviewer", reviewer));
        assertEquals(204, client.delete("/api/render/templates/import/Import-Reviewer", reviewer));
    }
}
