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
import java.util.ArrayList;
import java.util.List;
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
    @CacheInvalidateAll(cacheName = "bausteine")
    public Response importTemplate(ImportRequest request) {
        // Ungueltige Anfragen sind ein Fehler des Aufrufers (400), nicht des Servers (500)
        String invalid = validate(request);
        if (invalid != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"" + invalid + "\"}")
                    .build();
        }
        byte[] content = Base64.getDecoder().decode(request.contentBase64());

        // Delete existing template with same ID if present (upsert)
        ProductionTemplate.delete("id", request.id());

        // Create and persist new template
        ProductionTemplate template = new ProductionTemplate();
        template.id = request.id();
        template.name = request.name();
        template.version = request.version();
        template.content = content;
        template.validFrom = request.validFrom();
        template.validUntil = request.validUntil();
        template.type = request.type() == null ? TemplateType.TEMPLATE : TemplateType.valueOf(request.type());
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
        templateCache.invalidateBaustein(name);
        return Response.noContent().build();
    }

    /** @return Fehlerbeschreibung oder {@code null}, wenn die Anfrage vollstaendig und gueltig ist. */
    static String validate(ImportRequest request) {
        if (request == null) {
            return "request body is missing";
        }
        List<String> missing = new ArrayList<>();
        if (request.id() == null) missing.add("id");
        if (request.name() == null || request.name().isBlank()) missing.add("name");
        if (request.version() == null) missing.add("version");
        if (request.contentBase64() == null || request.contentBase64().isBlank()) missing.add("contentBase64");
        if (request.validFrom() == null) missing.add("validFrom");
        if (!missing.isEmpty()) {
            return "missing required fields: " + String.join(", ", missing);
        }
        try {
            Base64.getDecoder().decode(request.contentBase64());
        } catch (IllegalArgumentException e) {
            return "contentBase64 is not valid Base64";
        }
        if (request.type() != null && !java.util.EnumSet.allOf(TemplateType.class).stream()
                .map(Enum::name).toList().contains(request.type())) {
            return "type must be TEMPLATE or BAUSTEIN";
        }
        return null;
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
        java.time.LocalDateTime validUntil,  // null = kein Ablauf
        String type                          // TEMPLATE (Standard) oder BAUSTEIN, ADR-0018
    ) {}
}
