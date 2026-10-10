package io.github.flaechsig.blocpress.render;

import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Engine-Modus (ADR-0016): render startet und rendert ohne Datenbank; die Datenbank-URL zeigt
 * hier bewusst ins Leere, Dev Services sind aus.
 */
@QuarkusTest
@TestProfile(EngineModeTest.Engine.class)
class EngineModeTest {

    public static class Engine implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "blocpress.render.mode", "engine",
                    "quarkus.datasource.jdbc.url", "jdbc:postgresql://127.0.0.1:1/unreachable",
                    "quarkus.datasource.devservices.enabled", "false");
        }
    }

    @TestHTTPResource("/")
    URI base;

    @Inject
    Scheduler scheduler;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("REQ-0068: rendersTemplateSentWithTheRequestWithoutDatabase")
    void rendersTemplateSentWithTheRequestWithoutDatabase() throws Exception {
        assertEquals(200, new AuthTestClient(base).renderInline(null));
    }

    @ParameterizedTest(name = "{displayName} [{0} {1}]")
    @DisplayName("REQ-0069: databasePathsAnswer404NamingTheMode")
    @CsvSource({
            "POST, /api/render/rechnung",
            "POST, /api/render/jobs",
            "GET,  /api/render/jobs/00000000-0000-0000-0000-000000000000",
            "GET,  /api/render/dashboard",
            "GET,  /api/render/dashboard/jobs",
            "POST, /api/render/templates/import",
            "DELETE, /api/render/templates/import/rechnung"
    })
    void databasePathsAnswer404NamingTheMode(String method, String path) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(base.resolve(path))
                        .method(method, HttpRequest.BodyPublishers.ofString("{}"))
                        .header("Content-Type", "application/json").build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("engine mode"), response.body());
        assertFalse(response.body().toLowerCase().contains("exception"), response.body());
        assertFalse(response.body().toLowerCase().contains("hibernate"), response.body());
    }

    @Test
    @DisplayName("REQ-0070: noScheduledTaskAndReadinessWithoutDatabaseCheck")
    void noScheduledTaskAndReadinessWithoutDatabaseCheck() throws Exception {
        assertFalse(scheduler.isRunning(), "scheduler runs in engine mode");

        HttpResponse<String> ready = http.send(HttpRequest.newBuilder(base.resolve("/q/health/ready")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ready.statusCode(), ready.body());
        assertFalse(ready.body().contains("Database"), "readiness checks the database: " + ready.body());
    }
}
