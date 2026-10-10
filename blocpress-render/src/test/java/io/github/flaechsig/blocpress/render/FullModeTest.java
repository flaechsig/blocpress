package io.github.flaechsig.blocpress.render;

import io.quarkus.scheduler.Scheduler;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gegenprobe zu REQ-0070: Ohne Engine-Modus (Standard {@code full}) laufen Scheduler und der
 * Datenbank-Check der Readiness wie bisher.
 */
@QuarkusTest
class FullModeTest {

    @TestHTTPResource("/q/health/ready")
    URI ready;

    @Inject
    Scheduler scheduler;

    @Test
    @DisplayName("REQ-0070: fullModeKeepsSchedulerAndDatabaseCheck")
    void fullModeKeepsSchedulerAndDatabaseCheck() throws Exception {
        assertTrue(scheduler.isRunning(), "scheduler does not run in full mode");
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(ready).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("Database connections health check"), response.body());
    }
}
