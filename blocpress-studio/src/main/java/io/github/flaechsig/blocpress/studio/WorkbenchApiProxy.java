package io.github.flaechsig.blocpress.studio;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Proxies all /api/* requests from the browser to the internal workbench server.
 *
 * WHY: blocpress-workbench runs on an internal port (8082) that is not exposed
 * to the host. The studio is the single public entry point on port 8080.
 * The browser always calls /api/* on the same origin (studio), and this proxy
 * forwards those calls to workbench, forwarding Authorization and Content-Type
 * headers so JWT auth and multipart uploads work transparently.
 */
@Path("/api")
public class WorkbenchApiProxy {

    @ConfigProperty(name = "workbench.url", defaultValue = "http://localhost:8082")
    String workbenchUrl;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

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

        String contentType = headers.getHeaderString("Content-Type");
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        String authorization = headers.getHeaderString("Authorization");
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }

        HttpRequest.BodyPublisher publisher = (body != null && body.length > 0)
                ? HttpRequest.BodyPublishers.ofByteArray(body)
                : HttpRequest.BodyPublishers.noBody();

        HttpResponse<byte[]> resp = HTTP_CLIENT.send(
                builder.method(method, publisher).build(),
                HttpResponse.BodyHandlers.ofByteArray());

        Response.ResponseBuilder rb = Response.status(resp.statusCode()).entity(resp.body());
        resp.headers().firstValue("Content-Type")
                .ifPresent(ct -> rb.header("Content-Type", ct));
        resp.headers().firstValue("Content-Disposition")
                .ifPresent(cd -> rb.header("Content-Disposition", cd));
        return rb.build();
    }
}
