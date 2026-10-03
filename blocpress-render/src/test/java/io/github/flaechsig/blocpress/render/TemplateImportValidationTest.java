package io.github.flaechsig.blocpress.render;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ungueltige Import-Anfragen ergeben 400 mit Meldung statt 500 (bis 2.6.1: NullPointerException). */
@QuarkusTest
class TemplateImportValidationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @TestHTTPResource("/")
    URI base;

    @Test
    void emptyRequestIsRejectedWithMissingFields() throws Exception {
        var response = post("{}");
        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.body().contains("id") && response.body().contains("contentBase64")
                && response.body().contains("validFrom"), response.body());
    }

    @Test
    void invalidBase64IsRejected() throws Exception {
        var response = post("{\"id\":\"" + UUID.randomUUID() + "\",\"name\":\"x\",\"version\":1,"
                + "\"contentBase64\":\"%%% kein base64 %%%\",\"validFrom\":\"2026-01-01T00:00:00\"}");
        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.body().contains("Base64"), response.body());
    }

    private HttpResponse<String> post(String json) throws Exception {
        return http.send(HttpRequest.newBuilder(base.resolve("/api/render/templates/import"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
