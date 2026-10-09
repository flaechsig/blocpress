package io.github.flaechsig.blocpress.render;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** REQ-0055: Bei ausgeschalteter Absicherung bleiben Import und Entfernen ohne Token moeglich. */
@QuarkusTest
class ImportAuthDisabledTest {

    @TestHTTPResource("/")
    URI base;

    @Test
    @DisplayName("REQ-0055: importAndRemoveWorkWithoutTokenWhileDisabled")
    void importAndRemoveWorkWithoutTokenWhileDisabled() throws Exception {
        var client = new AuthTestClient(base);
        assertEquals(200, client.importTemplate("Import-offen", null));
        assertEquals(204, client.delete("/api/render/templates/import/Import-offen", null));
    }
}
