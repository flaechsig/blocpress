package io.github.flaechsig.blocpress.render;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-0098: render startet beim Hochfahren eine Instanz je Worker und meldet sie in der Readiness. */
@QuarkusTest
class LibreOfficeReadinessTest {

    @TestHTTPResource("/q/health/ready")
    URI ready;

    @Test
    @DisplayName("REQ-0098: readinessReportsOneRunningInstancePerWorker")
    void readinessReportsOneRunningInstancePerWorker() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(ready).GET().build(), HttpResponse.BodyHandlers.ofString());
        String body = response.body().replaceAll("\\s", "");

        assertEquals(200, response.statusCode(), response.body());
        // %test: zwei Worker (application.properties)
        assertTrue(body.contains("\"name\":\"LibreOfficeinstances\",\"status\":\"UP\",\"data\":{\"instances\":2}"), response.body());
    }
}
