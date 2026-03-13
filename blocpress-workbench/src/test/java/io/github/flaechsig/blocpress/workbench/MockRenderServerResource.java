package io.github.flaechsig.blocpress.workbench;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Starts a lightweight in-process HTTP server that pretends to be the render service.
 * Tests configure {@link #statusCode} and {@link #responseBody} before calling the
 * preview endpoint. The server is started on a random port; the workbench's render URL
 * is overridden to point to it.
 */
public class MockRenderServerResource implements QuarkusTestResourceLifecycleManager {

    /** HTTP status code the mock render will return (default: 500 = render failure). */
    public static volatile int statusCode = 500;
    /** Body bytes the mock render will return. */
    public static volatile byte[] responseBody = "LibreOffice conversion failed (exit=1)".getBytes();
    /** Content-Type header of the mock response. */
    public static volatile String responseContentType = "text/plain";

    private HttpServer server;

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                byte[] body = responseBody;
                exchange.getResponseHeaders().set("Content-Type", responseContentType);
                exchange.sendResponseHeaders(statusCode, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            int port = server.getAddress().getPort();
            // The workbench reads renderUrl from this config property
            // and calls renderUrl + "/render/template"
            return Map.of("quarkus.rest-client.\"render\".url", "http://localhost:" + port);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start mock render server", e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** Convenience: configure mock to return a minimal valid PDF. */
    public static void respondWithPdf() {
        // Minimal PDF magic bytes — enough for the workbench to return 200 application/pdf
        responseBody = new byte[]{
            '%', 'P', 'D', 'F', '-', '1', '.', '4', '\n',
            '%', (byte)0xE2, (byte)0xE3, (byte)0xCF, (byte)0xD3, '\n'
        };
        responseContentType = "application/pdf";
        statusCode = 200;
    }

    /** Convenience: configure mock to return a render-side 500. */
    public static void respondWithServerError(String message) {
        responseBody = message.getBytes();
        responseContentType = "text/plain";
        statusCode = 500;
    }
}
