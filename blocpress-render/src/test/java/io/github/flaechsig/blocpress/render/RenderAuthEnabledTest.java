package io.github.flaechsig.blocpress.render;

import org.junit.jupiter.api.DisplayName;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static io.github.flaechsig.blocpress.render.AuthTestClient.devToken;
import static io.github.flaechsig.blocpress.render.AuthTestClient.forgedToken;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** REQ-0008: bei eingeschaltetem JWT verlangen Render-, Job- und Dashboard-Endpunkte ein gueltiges Token. */
@QuarkusTest
@TestProfile(RenderAuthEnabledTest.AuthEnabled.class)
@DisplayName("REQ-0008: RenderAuthEnabledTest")
class RenderAuthEnabledTest {

    public static class AuthEnabled implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "blocpress.auth.enabled", "true",
                    "mp.jwt.verify.publickey", AuthTestClient.DEV_PUBLIC_KEY,
                    "mp.jwt.verify.issuer", AuthTestClient.DEV_ISSUER);
        }
    }

    @TestHTTPResource("/")
    URI base;

    @Test
    void renderingRequiresValidToken() throws Exception {
        var client = new AuthTestClient(base);
        assertEquals(401, client.renderInline(null));
        assertEquals(401, client.renderInline(forgedToken()));
        assertEquals(200, client.renderInline(devToken()));
    }

    @Test
    void renderByNameJobsAndDashboardRequireToken() throws Exception {
        var client = new AuthTestClient(base);
        assertEquals(401, client.postJson("/api/render/Rechnung", "{\"data\":{},\"outputType\":\"pdf\"}", null));
        assertEquals(401, client.get("/api/render/jobs/00000000-0000-0000-0000-000000000000", null));
        assertEquals(401, client.get("/api/render/dashboard", null));
        assertEquals(200, client.get("/api/render/dashboard", devToken()));
    }

    @Test
    void healthStaysOpen() throws Exception {
        // Der Import ist seit ADR-0019 nicht mehr offen, siehe ImportAuthEnabledTest (REQ-0054)
        assertEquals(200, new AuthTestClient(base).get("/q/health/live", null));
    }
}
