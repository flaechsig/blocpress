package io.github.flaechsig.blocpress.render;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** REQ-0067: render sendet nosniff mit jeder Antwort. */
@QuarkusTest
class SecurityHeadersTest {

    @TestHTTPResource("/")
    URI base;

    @ParameterizedTest(name = "{displayName} [{0}]")
    @DisplayName("REQ-0067: responseForbidsSniffing")
    @ValueSource(strings = {"/", "/api/render/dashboard", "/q/health"})
    void responseForbidsSniffing(String path) throws Exception {
        HttpResponse<Void> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(base.resolve(path)).GET().build(),
                HttpResponse.BodyHandlers.discarding());

        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(null));
    }
}
