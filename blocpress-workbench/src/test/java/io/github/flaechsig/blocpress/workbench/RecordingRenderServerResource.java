package io.github.flaechsig.blocpress.workbench;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mock des Render-Service, der jeden Aufruf mitschreibt (Methode, Pfad, Body). So laesst sich pruefen,
 * was die Workbench beim Auto-Deploy (POST …/render/templates/import) bzw. beim Ausmustern
 * (DELETE …/render/templates/import/{name}) an render schickt — und ein Ausfall simulieren.
 */
public class RecordingRenderServerResource implements QuarkusTestResourceLifecycleManager {

    public record Call(String method, String path, String body) {}

    public static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    /** Status, mit dem render auf Aufrufe antwortet (500 = render-Ausfall simulieren). */
    public static volatile int status = 200;
    /** Antwort auf POST …/render/template (Vorschau, Regressionslauf), z.B. ein PDF. */
    public static volatile byte[] renderResponse = new byte[0];

    private HttpServer server;

    public static void reset() {
        CALLS.clear();
        status = 200;
        renderResponse = new byte[0];
    }

    public static List<Call> calls(String method, String pathPrefix) {
        return CALLS.stream().filter(c -> c.method().equals(method) && c.path().startsWith(pathPrefix)).toList();
    }

    @Override
    public Map<String, String> start() {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String path = exchange.getRequestURI().getPath();
                CALLS.add(new Call(exchange.getRequestMethod(), path, body));
                byte[] response = path.endsWith("/render/template") ? renderResponse : new byte[0];
                exchange.sendResponseHeaders(status, response.length == 0 ? -1 : response.length);
                if (response.length > 0) {
                    try (var out = exchange.getResponseBody()) {
                        out.write(response);
                    }
                }
                exchange.close();
            });
            server.start();
            return Map.of("quarkus.rest-client.\"render\".url", "http://localhost:" + server.getAddress().getPort());
        } catch (IOException e) {
            throw new RuntimeException("Mock-Render-Server konnte nicht starten", e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
