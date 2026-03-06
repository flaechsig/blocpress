package io.github.flaechsig.blocpress.studio;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Proxies bp-workbench.js from the workbench server through the studio server.
 *
 * WHY: Dynamic ES module import() is subject to strict CORS enforcement in all
 * browsers. Quarkus's CORS filter applies to JAX-RS routes but not reliably to
 * the Vert.x StaticHandler. By proxying the JS file through the studio backend,
 * the browser imports from the same origin (no CORS needed at all).
 *
 * RETRY: Quarkus dev-mode hot-reloads briefly serve HTML error pages or 5xx
 * responses while recompiling. We retry server-side so the browser sees a clean
 * response once Quarkus is ready.
 */
@Path("/proxy")
public class WorkbenchProxyResource {

    @ConfigProperty(name = "workbench.url", defaultValue = "http://localhost:8081")
    String workbenchUrl;

    private static final int MAX_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 1500;

    @GET
    @Path("/bp-workbench.js")
    @Produces("application/javascript")
    public Response getWorkbenchJs() {
        String jsUrl = workbenchUrl.replaceAll("/+$", "") + "/components/bp-workbench.js";
        Exception lastError = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                if (attempt > 1) {
                    Thread.sleep(RETRY_DELAY_MS);
                }

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(4))
                        .build();

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(jsUrl))
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();

                HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());

                if (resp.statusCode() == 200) {
                    // Validate: Quarkus hot-reload may briefly return HTML instead of JS
                    String preview = new String(resp.body(), 0, Math.min(200, resp.body().length));
                    if (preview.stripLeading().startsWith("<")) {
                        lastError = new RuntimeException("Server returned HTML (hot-reload in progress, attempt " + attempt + ")");
                        continue;
                    }
                    return Response.ok(resp.body())
                            .header("Cache-Control", "no-store")
                            .header("Content-Type", "application/javascript; charset=UTF-8")
                            .build();
                }

                lastError = new RuntimeException("HTTP " + resp.statusCode());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                lastError = e;
            }
        }

        // IMPORTANT: Never return a non-JS HTTP error (4xx/5xx) here.
        // Browsers report any non-JS response from import() as the generic
        // "error loading dynamically imported module" — the actual error is invisible.
        // Instead, return valid JS that throws a readable error message.
        String msg = (lastError != null ? lastError.getMessage() : "unknown error")
                .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
        String errorJs = "throw new Error(\"Workbench nicht erreichbar (nach " + MAX_ATTEMPTS + " Versuchen): " + msg + "\");";
        return Response.ok(errorJs)
                .header("Cache-Control", "no-store")
                .header("Content-Type", "application/javascript; charset=UTF-8")
                .build();
    }
}
