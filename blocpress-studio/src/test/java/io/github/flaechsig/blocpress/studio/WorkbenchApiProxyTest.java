package io.github.flaechsig.blocpress.studio;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * REQ-0029: Das Studio leitet WebDAV-Anfragen unter /api/webdav mit Methode, Headern und Body an
 * die Workbench weiter und gibt deren Antwort samt Headern zurück. Die Workbench ist hier eine
 * Attrappe, die die eingehende Anfrage aufzeichnet.
 */
@QuarkusTest
@QuarkusTestResource(WorkbenchApiProxyTest.FakeWorkbench.class)
class WorkbenchApiProxyTest {

    /** Zuletzt bei der Attrappe angekommen: Methode, Pfad, Depth, Lock-Token, Body. */
    static final AtomicReference<String> RECEIVED = new AtomicReference<>();

    @TestHTTPResource("/api/webdav/templates/")
    URL collection;

    private final HttpClient client = HttpClient.newHttpClient();

    @ParameterizedTest(name = "{displayName} [{0}]")
    @DisplayName("REQ-0029: webDavMethodReachesWorkbenchWithHeadersAndBody")
    @ValueSource(strings = {"PROPFIND", "OPTIONS", "LOCK", "UNLOCK", "GET", "PUT"})
    void webDavMethodReachesWorkbenchWithHeadersAndBody(String method) throws Exception {
        boolean withBody = method.equals("PROPFIND") || method.equals("LOCK") || method.equals("PUT");
        var request = HttpRequest.newBuilder(URI.create(collection + "rechnung.odt"))
                .header("Depth", "1")
                .header("Lock-Token", "<opaquelocktoken:42>")
                .method(method, withBody
                        ? HttpRequest.BodyPublishers.ofString("<propfind/>")
                        : HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(207, response.statusCode());
        assertEquals(method + " /api/webdav/templates/rechnung.odt depth=1 lock=<opaquelocktoken:42> body="
                + (withBody ? "<propfind/>" : ""), RECEIVED.get());
        assertEquals("1", response.headers().firstValue("DAV").orElse(null));
        assertEquals("<opaquelocktoken:42>", response.headers().firstValue("Lock-Token").orElse(null));
        assertEquals("\"v1\"", response.headers().firstValue("ETag").orElse(null));
    }

    @Test
    @DisplayName("REQ-0029: absoluteWorkbenchLocationBecomesServerRelative")
    void absoluteWorkbenchLocationBecomesServerRelative() throws Exception {
        var request = HttpRequest.newBuilder(URI.create(collection + "neu.odt"))
                .PUT(HttpRequest.BodyPublishers.ofString("odt"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(207, response.statusCode());
        assertEquals("/api/webdav/templates/neu.odt", response.headers().firstValue("Location").orElse(null));
    }

    /** Startet die Attrappe der Workbench und richtet {@code workbench.url} auf sie. */
    public static class FakeWorkbench implements QuarkusTestResourceLifecycleManager {

        private HttpServer server;

        @Override
        public Map<String, String> start() {
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            String base = "http://localhost:" + server.getAddress().getPort();
            server.createContext("/", exchange -> {
                var h = exchange.getRequestHeaders();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                RECEIVED.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                        + " depth=" + h.getFirst("Depth") + " lock=" + h.getFirst("Lock-Token") + " body=" + body);
                var out = exchange.getResponseHeaders();
                out.add("DAV", "1");
                out.add("ETag", "\"v1\"");
                out.add("Lock-Token", "<opaquelocktoken:42>");
                out.add("Location", base + exchange.getRequestURI().getPath());
                out.add("Content-Type", "application/xml");
                byte[] reply = "<multistatus/>".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(207, reply.length);
                exchange.getResponseBody().write(reply);
                exchange.close();
            });
            server.start();
            return Map.of("workbench.url", base);
        }

        @Override
        public void stop() {
            server.stop(0);
        }
    }
}
