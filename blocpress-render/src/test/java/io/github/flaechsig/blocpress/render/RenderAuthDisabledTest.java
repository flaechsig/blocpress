package io.github.flaechsig.blocpress.render;

import org.junit.jupiter.api.DisplayName;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.github.flaechsig.blocpress.render.AuthTestClient.forgedToken;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** REQ-0008: im Default (JWT aus) bleibt die Render-API ohne Token erreichbar. */
@QuarkusTest
@DisplayName("REQ-0008: RenderAuthDisabledTest")
class RenderAuthDisabledTest {

    @TestHTTPResource("/")
    URI base;

    @Test
    void renderingWorksWithoutToken() throws Exception {
        var client = new AuthTestClient(base);
        assertEquals(200, client.renderInline(null));
        assertEquals(200, client.get("/api/render/dashboard", null));
    }

    @Test
    void tokenSentByClientIsIgnoredWhileDisabled() throws Exception {
        // Bestehende Clients schicken teils ein Token mit; ohne konfigurierten Schluessel darf
        // das nicht zu 401 fuehren (quarkus.http.auth.proactive=false).
        assertEquals(200, new AuthTestClient(base).renderInline(forgedToken()));
    }
}
