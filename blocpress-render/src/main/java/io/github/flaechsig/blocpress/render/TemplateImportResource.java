package io.github.flaechsig.blocpress.render;

import io.quarkus.cache.CacheInvalidate;
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

import java.time.LocalDateTime;
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
 * Access (ADR-0019): with BLOCPRESS_AUTH_ENABLED=true the HTTP permission "import" in
 * application.properties requires a token with the group reviewer, which workbench forwards
 * from the user; with authentication disabled the endpoint stays open (@PermitAll).
 */
@ApplicationScoped
@Path("api/render/templates/import")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TemplateImportResource {

    @Inject
    TemplateContentCache contentCache;

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
    public Response importTemplate(ImportRequest request) {
        // Ungueltige Anfragen sind ein Fehler des Aufrufers (400), nicht des Servers (500)
        String invalid = validate(request);
        if (invalid != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"" + invalid + "\"}")
                    .build();
        }
        byte[] content = Base64.getDecoder().decode(request.contentBase64());

        // Delete existing template with same ID if present (upsert); welche Version gilt, ermittelt
        // render bei jedem Rendern neu, nur der Inhalt dieser id liegt im Cache (REQ-0063)
        ProductionTemplate.delete("id", request.id());
        contentCache.invalidate(request.id());

        // Create and persist new template
        ProductionTemplate template = new ProductionTemplate();
        template.id = request.id();
        template.name = request.name();
        template.version = request.version();
        template.content = content;
        template.validFrom = request.validFrom();
        template.validUntil = request.validUntil();
        template.type = request.type() == null ? TemplateType.TEMPLATE : TemplateType.valueOf(request.type());
        endPreviousVersions(template);
        template.persist();

        return Response.ok().build();
    }

    /**
     * Beendet die bis dahin gueltige Version desselben Namens zum Beginn der importierten, wie es die
     * Workbench bei der Freigabe tut; so gilt auch in production hoechstens eine Version je Zeitpunkt
     * (REQ-0040), im selben Schritt wie der Import.
     */
    private static void endPreviousVersions(ProductionTemplate imported) {
        List<ProductionTemplate> others = ProductionTemplate.list("name = ?1 AND id <> ?2", imported.name, imported.id);
        for (ProductionTemplate other : others) {
            if (!other.validFrom.isAfter(imported.validFrom)
                    && (other.validUntil == null || other.validUntil.isAfter(imported.validFrom))) {
                other.validUntil = imported.validFrom;
            }
        }
    }

    /**
     * Zieht einen Namen aus production zurueck (Workbench: RETIRED). Geloescht wird nichts: Jede
     * Version, die noch gilt oder kuenftig gelten wuerde, endet jetzt (REQ-0037). So bleibt
     * nachvollziehbar, welche Version zu welchem Zeitpunkt galt.
     * Endpoint: DELETE /api/render/templates/import/{name}
     *
     * @param name Template name to retire
     * @return 204 No Content on success
     */
    @DELETE
    @Path("{name}")
    @PermitAll
    @Transactional
    public Response removeTemplate(@PathParam("name") String name) {
        LocalDateTime now = LocalDateTime.now();
        ProductionTemplate.update("validUntil = ?1 WHERE name = ?2 AND (validUntil IS NULL OR validUntil > ?1)",
                now, name);
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
