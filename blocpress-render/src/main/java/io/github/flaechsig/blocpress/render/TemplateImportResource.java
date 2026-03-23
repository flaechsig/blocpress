package io.github.flaechsig.blocpress.render;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheInvalidateAll;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Base64;
import java.util.UUID;

/**
 * Internal API endpoint for importing approved templates from workbench
 * into the production schema. This endpoint is called automatically by
 * blocpress-workbench when a template transitions to APPROVED status.
 *
 * Endpoint: POST /api/render/templates/import
 *
 * No authentication required — this is an internal endpoint only accessible
 * within the deployment infrastructure.
 */
@ApplicationScoped
@Path("api/render/templates/import")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TemplateImportResource {

    @Inject
    TemplateCache templateCache;

    /**
     * Import (or update) a template into the production schema.
     * If a template with the same ID already exists, it will be replaced (upsert semantics).
     *
     * @param request Import request with template data
     * @return 200 OK on success
     */
    @POST
    @PermitAll
    @Transactional
    @CacheInvalidateAll(cacheName = "templates")
    public Response importTemplate(ImportRequest request) {
        // Delete existing template with same ID if present (upsert)
        ProductionTemplate.delete("id", request.id());

        // Create and persist new template
        ProductionTemplate template = new ProductionTemplate();
        template.id = request.id();
        template.name = request.name();
        template.version = request.version();
        template.content = Base64.getDecoder().decode(request.contentBase64());
        template.validFrom = request.validFrom();
        template.validUntil = request.validUntil();
        template.persist();

        return Response.ok().build();
    }

    /**
     * Remove a template from the production schema (called when transitioning to RETIRED).
     * Endpoint: DELETE /api/render/templates/import/{name}
     *
     * @param name Template name to remove
     * @return 204 No Content on success
     */
    @DELETE
    @Path("{name}")
    @PermitAll
    @Transactional
    public Response removeTemplate(@PathParam("name") String name) {
        ProductionTemplate.delete("name", name);
        templateCache.invalidate(name);
        return Response.noContent().build();
    }

    /**
     * Request body for importing a template into production.
     */
    public record ImportRequest(
        UUID id,
        String name,
        Integer version,
        String contentBase64,       // Base64-encoded ODT binary
        java.time.LocalDateTime validFrom,
        java.time.LocalDateTime validUntil  // null = kein Ablauf
    ) {}
}
