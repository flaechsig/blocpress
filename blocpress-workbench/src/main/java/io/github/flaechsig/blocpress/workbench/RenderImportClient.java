package io.github.flaechsig.blocpress.workbench;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * REST Client for communicating with blocpress-render's internal template import API.
 *
 * When a template transitions to APPROVED status in workbench, this client
 * automatically pushes the template content to render's production schema
 * via POST /api/render/templates/import.
 *
 * This creates the "clean cut" between workbench (development) and render (production):
 * render no longer depends on workbench for template content — it has its own copy.
 */
@RegisterRestClient(configKey = "render-import")
@Path("/api/render/templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface RenderImportClient {

    /**
     * Import (deploy) an approved template into the production schema.
     *
     * @param request Import request with template metadata and base64-encoded ODT binary
     * @return Response (200 OK on success, error status on failure)
     */
    @POST
    @Path("/import")
    Response importTemplate(ImportRequest request);

    /**
     * Request body for importing a template into production.
     * All fields are required.
     */
    record ImportRequest(
        UUID id,
        String name,
        Integer version,
        String contentBase64,    // Base64-encoded ODT binary
        LocalDateTime validFrom  // Timestamp when template becomes active (may be null until APPROVED)
    ) {}
}
