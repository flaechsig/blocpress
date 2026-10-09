package io.github.flaechsig.blocpress.studio;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-0065, REQ-0066: Startseite und weitere Antworten tragen nosniff, DENY und eine
 * Content-Security-Policy, die Skripte nur von der eigenen Origin, esm.sh und der Import-Map
 * der Startseite zulaesst.
 */
@QuarkusTest
class SecurityHeadersTest {

    @TestHTTPResource("/")
    URI base;

    private final HttpClient client = HttpClient.newHttpClient();

    @ParameterizedTest(name = "{displayName} [{0}]")
    @DisplayName("REQ-0065: responseForbidsSniffingAndFraming")
    @ValueSource(strings = {"/", "/components/bp-app.js"})
    void responseForbidsSniffingAndFraming(String path) throws Exception {
        HttpResponse<Void> response = get(path);

        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(null));
        assertEquals("DENY", response.headers().firstValue("X-Frame-Options").orElse(null));
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @DisplayName("REQ-0066: responseCarriesContentSecurityPolicy")
    @ValueSource(strings = {"/", "/components/bp-app.js"})
    void responseCarriesContentSecurityPolicy(String path) throws Exception {
        String csp = get(path).headers().firstValue("Content-Security-Policy").orElse(null);

        assertNotNull(csp);
        String scriptSrc = directive(csp, "script-src");
        assertEquals("'self' https://esm.sh '" + importMapHash() + "'", scriptSrc);
        assertFalse(scriptSrc.contains("unsafe"), scriptSrc);
        assertEquals("'self'", directive(csp, "default-src"));
        assertEquals("'none'", directive(csp, "frame-ancestors"));
    }

    private HttpResponse<Void> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(base.resolve(path)).GET().build(),
                HttpResponse.BodyHandlers.discarding());
    }

    private static String directive(String csp, String name) {
        for (String part : csp.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + " ")) {
                return trimmed.substring(name.length() + 1).trim();
            }
        }
        throw new AssertionError(name + " fehlt in " + csp);
    }

    /** Hash der Import-Map, wie ihn der Browser fuer das Inline-Skript bildet. */
    private static String importMapHash() throws Exception {
        String html;
        try (InputStream in = SecurityHeadersTest.class.getResourceAsStream("/META-INF/resources/index.html")) {
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher m = Pattern.compile("<script type=\"importmap\">(.*?)</script>", Pattern.DOTALL).matcher(html);
        assertTrue(m.find(), "Import-Map in index.html");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(m.group(1).getBytes(StandardCharsets.UTF_8));
        return "sha256-" + Base64.getEncoder().encodeToString(digest);
    }
}
