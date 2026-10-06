package io.github.flaechsig.blocpress.studio;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * Proxies all /api/* requests from the browser to the internal workbench server.
 *
 * WHY: blocpress-workbench runs on an internal port (8082) that is not exposed
 * to the host. The studio is the single public entry point on port 8080.
 * The browser always calls /api/* on the same origin (studio), and this proxy
 * forwards those calls to workbench, forwarding Authorization and Content-Type
 * headers so JWT auth and multipart uploads work transparently.
 *
 * WebDAV (US-0046): LibreOffice opens templates under /api/webdav through the studio. The
 * proxy therefore also forwards OPTIONS, HEAD, PROPFIND, LOCK and UNLOCK, the WebDAV request
 * headers, and all response headers; an absolute Location pointing at the workbench becomes
 * server-relative.
 */
@Path("/api")
public class WorkbenchApiProxy {

    @ConfigProperty(name = "workbench.url", defaultValue = "http://localhost:8082")
    String workbenchUrl;

    /** Request-Header, die an die Workbench gehen (neben dem Body). */
    static final Set<String> REQUEST_HEADERS = Set.of(
            "Content-Type", "Authorization", "Accept", "Depth", "Destination", "Overwrite",
            "If", "Lock-Token", "Timeout", "If-Match", "If-None-Match");

    /** Antwort-Header, die nicht zurueckgegeben werden (hop-by-hop oder vom Server neu gesetzt). */
    static final Set<String> SKIPPED_RESPONSE_HEADERS = Set.of(
            "connection", "keep-alive", "transfer-encoding", "content-length", "upgrade",
            "proxy-authenticate", "proxy-authorization", "te", "trailer", ":status");

    // Lazy initialisiert: Ein lebender HttpClient (mit Threads/Selector) darf im
    // GraalVM-Native-Image nicht zur Build-Zeit in den Image-Heap geraten.
    private static volatile HttpClient httpClient;

    private static HttpClient httpClient() {
        HttpClient client = httpClient;
        if (client == null) {
            synchronized (WorkbenchApiProxy.class) {
                client = httpClient;
                if (client == null) {
                    client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .build();
                    httpClient = client;
                }
            }
        }
        return client;
    }

    @GET
    @Path("/{path: .*}")
    @Produces(MediaType.WILDCARD)
    public Response proxyGet(
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) throws Exception {
        return forward("GET", path, null, headers, uriInfo);
    }

    @POST
    @Path("/{path: .*}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response proxyPost(
            @PathParam("path") String path,
            byte[] body,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) throws Exception {
        return forward("POST", path, body, headers, uriInfo);
    }

    @PUT
    @Path("/{path: .*}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response proxyPut(
            @PathParam("path") String path,
            byte[] body,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) throws Exception {
        return forward("PUT", path, body, headers, uriInfo);
    }

    @DELETE
    @Path("/{path: .*}")
    @Produces(MediaType.WILDCARD)
    public Response proxyDelete(
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) throws Exception {
        return forward("DELETE", path, null, headers, uriInfo);
    }

    @OPTIONS
    @Path("/{path: .*}")
    @Produces(MediaType.WILDCARD)
    public Response proxyOptions(
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) throws Exception {
        return forward("OPTIONS", path, null, headers, uriInfo);
    }

    @HEAD
    @Path("/{path: .*}")
    @Produces(MediaType.WILDCARD)
    public Response proxyHead(
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) throws Exception {
        return forward("HEAD", path, null, headers, uriInfo);
    }

    @PROPFIND
    @Path("/{path: .*}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response proxyPropfind(
            @PathParam("path") String path,
            byte[] body,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) throws Exception {
        return forward("PROPFIND", path, body, headers, uriInfo);
    }

    @LOCK
    @Path("/{path: .*}")
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.WILDCARD)
    public Response proxyLock(
            @PathParam("path") String path,
            byte[] body,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) throws Exception {
        return forward("LOCK", path, body, headers, uriInfo);
    }

    @UNLOCK
    @Path("/{path: .*}")
    @Produces(MediaType.WILDCARD)
    public Response proxyUnlock(
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers) throws Exception {
        return forward("UNLOCK", path, null, headers, uriInfo);
    }

    private Response forward(String method, String path, byte[] body,
            HttpHeaders headers, UriInfo uriInfo) throws Exception {
        String target = workbenchUrl.replaceAll("/+$", "") + "/api/" + path;
        String query = uriInfo.getRequestUri().getRawQuery();
        if (query != null && !query.isEmpty()) {
            target += "?" + query;
        }

        var builder = HttpRequest.newBuilder()
                .uri(URI.create(target))
                .timeout(Duration.ofSeconds(120));

        for (String name : REQUEST_HEADERS) {
            String value = headers.getHeaderString(name);
            if (value != null) {
                builder.header(name, value);
            }
        }

        HttpRequest.BodyPublisher publisher = (body != null && body.length > 0)
                ? HttpRequest.BodyPublishers.ofByteArray(body)
                : HttpRequest.BodyPublishers.noBody();

        HttpResponse<byte[]> resp = httpClient().send(
                builder.method(method, publisher).build(),
                HttpResponse.BodyHandlers.ofByteArray());

        Response.ResponseBuilder rb = Response.status(resp.statusCode());
        if (resp.body().length > 0) {
            rb.entity(resp.body());
        }
        resp.headers().map().forEach((name, values) -> {
            if (SKIPPED_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                return;
            }
            for (String value : values) {
                rb.header(name, name.equalsIgnoreCase("Location") ? relativeLocation(value) : value);
            }
        });
        return rb.build();
    }

    /** Eine absolute Adresse der Workbench wird serverrelativ, damit der Client beim Studio bleibt. */
    String relativeLocation(String location) {
        String base = workbenchUrl.replaceAll("/+$", "");
        return location.startsWith(base + "/") ? location.substring(base.length()) : location;
    }
}
