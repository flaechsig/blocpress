package io.github.flaechsig.blocpress.workbench;

import io.github.flaechsig.blocpress.workbench.entity.Template;
import io.github.flaechsig.blocpress.workbench.entity.TemplateStatus;
import io.github.flaechsig.blocpress.workbench.entity.TemplateType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WebDAV endpoint for accessing templates and Bausteine via HTTP.
 *
 * URL-Schema:
 *   GET/PUT  /webdav/bausteine/{name}.odt          → latest DRAFT (read-write)
 *   GET      /webdav/released/bausteine/{name}.odt → latest APPROVED (read-only)
 *   GET/PUT  /webdav/templates/{name}.odt          → latest DRAFT (read-write)
 *   GET      /webdav/released/templates/{name}.odt → latest APPROVED (read-only)
 *   PROPFIND /webdav/{collection}/                 → directory listing (207)
 *   OPTIONS  /webdav/                              → DAV: 1
 */
@Path("webdav")
public class WebDavResource {

    private static final String ODT_CONTENT_TYPE = "application/vnd.oasis.opendocument.text";

    // ===== OPTIONS — DAV capability advertisement =====

    @OPTIONS
    public Response options() {
        return Response.ok()
                .header("DAV", "1")
                .header("Allow", "GET, PUT, OPTIONS, PROPFIND")
                .build();
    }

    // ===== GET — DRAFT (read-write) collections =====

    @GET
    @Path("{collection}/{name}.odt")
    @Produces(ODT_CONTENT_TYPE)
    public Response getDraft(@PathParam("collection") String collection,
                             @PathParam("name") String name) {
        TemplateType type = resolveType(collection);
        Template template = findLatestDraft(name, type);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return odtResponse(template);
    }

    // ===== GET — APPROVED (released, read-only) collections =====

    @GET
    @Path("released/{collection}/{name}.odt")
    @Produces(ODT_CONTENT_TYPE)
    public Response getReleased(@PathParam("collection") String collection,
                                @PathParam("name") String name) {
        TemplateType type = resolveType(collection);
        Template template = Template.findLatestActiveByName(name);
        if (template == null || template.type != type) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return odtResponse(template);
    }

    // ===== PUT — update DRAFT content =====

    @PUT
    @Path("{collection}/{name}.odt")
    @Transactional
    public Response putDraft(@PathParam("collection") String collection,
                             @PathParam("name") String name,
                             byte[] content) {
        TemplateType type = resolveType(collection);
        Template template = findLatestDraft(name, type);
        if (template == null) {
            // Create new DRAFT — WebDAV PUT semantics: create-or-update
            template = new Template();
            template.name = name;
            template.type = type;
            template.status = TemplateStatus.DRAFT;
            template.version = 1;
            template.validFrom = java.time.LocalDateTime.now();
            template.createdAt = java.time.LocalDateTime.now();
            template.content = content;
            template.persist();
            return Response.created(
                jakarta.ws.rs.core.UriBuilder.fromPath("/api/webdav/{c}/{n}.odt")
                    .build(collection, name)
            ).build();
        }
        if (template.status != TemplateStatus.DRAFT) {
            throw new WebApplicationException(
                "Only DRAFT documents can be updated via WebDAV",
                Response.Status.FORBIDDEN
            );
        }
        template.content = content;
        template.persist();
        return Response.noContent().build();
    }

    // ===== PUT — released is read-only =====

    @PUT
    @Path("released/{collection}/{name}.odt")
    public Response putReleased() {
        throw new WebApplicationException(
            "Released documents are read-only",
            Response.Status.FORBIDDEN
        );
    }

    // ===== PROPFIND — directory listing (JAX-RS custom method via @HttpMethod) =====

    @OPTIONS
    @Path("{collection}/")
    public Response optionsCollection() {
        return Response.ok()
                .header("DAV", "1")
                .header("Allow", "GET, PUT, OPTIONS, PROPFIND")
                .build();
    }

    @OPTIONS
    @Path("released/{collection}/")
    public Response optionsReleasedCollection() {
        return Response.ok()
                .header("DAV", "1")
                .header("Allow", "GET, OPTIONS, PROPFIND")
                .build();
    }

    @PROPFIND
    @Path("{collection}/")
    @Produces("application/xml")
    public Response propfind(@PathParam("collection") String collection) {
        return buildPropfind(collection, false);
    }

    @PROPFIND
    @Path("{collection}/{name}.odt")
    @Produces("application/xml")
    public Response propfindFile(@PathParam("collection") String collection,
                                 @PathParam("name") String name) {
        TemplateType type = resolveType(collection);
        Template template = findLatestDraft(name, type);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return buildFilePropfind("/api/webdav/" + collection + "/" + name + ".odt", name, template);
    }

    @PROPFIND
    @Path("released/{collection}/")
    @Produces("application/xml")
    public Response propfindReleased(@PathParam("collection") String collection) {
        return buildPropfind(collection, true);
    }

    @PROPFIND
    @Path("released/{collection}/{name}.odt")
    @Produces("application/xml")
    public Response propfindReleasedFile(@PathParam("collection") String collection,
                                         @PathParam("name") String name) {
        TemplateType type = resolveType(collection);
        Template template = Template.findLatestActiveByName(name);
        if (template == null || template.type != type) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return buildFilePropfind("/api/webdav/released/" + collection + "/" + name + ".odt", name, template);
    }

    // ===== Helpers =====

    private TemplateType resolveType(String collection) {
        if ("bausteine".equalsIgnoreCase(collection)) return TemplateType.BAUSTEIN;
        if ("templates".equalsIgnoreCase(collection)) return TemplateType.TEMPLATE;
        throw new WebApplicationException(
            "Unknown collection '" + collection + "'. Use 'bausteine' or 'templates'.",
            Response.Status.NOT_FOUND
        );
    }

    private Template findLatestDraft(String name, TemplateType type) {
        return Template.<Template>find(
            "name = ?1 AND type = ?2 ORDER BY version DESC", name, type
        ).firstResult();
    }

    private Response odtResponse(Template template) {
        return Response.ok(template.content)
                .type(ODT_CONTENT_TYPE)
                .header("Content-Disposition", "attachment; filename=\"" + template.name + ".odt\"")
                .header("X-Template-Name", template.name)
                .header("X-Template-Status", template.status.toString())
                .header("X-Template-Version", template.version.toString())
                .build();
    }

    private Response buildFilePropfind(String href, String name, Template template) {
        long size = template.content != null ? template.content.length : 0;
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<D:multistatus xmlns:D=\"DAV:\">\n" +
            "  <D:response>\n" +
            "    <D:href>" + href + "</D:href>\n" +
            "    <D:propstat>\n" +
            "      <D:prop>\n" +
            "        <D:displayname>" + name + ".odt</D:displayname>\n" +
            "        <D:getcontenttype>" + ODT_CONTENT_TYPE + "</D:getcontenttype>\n" +
            "        <D:getcontentlength>" + size + "</D:getcontentlength>\n" +
            "        <D:resourcetype/>\n" +
            "      </D:prop>\n" +
            "      <D:status>HTTP/1.1 200 OK</D:status>\n" +
            "    </D:propstat>\n" +
            "  </D:response>\n" +
            "</D:multistatus>";
        return Response.status(207)
                .type("application/xml; charset=utf-8")
                .entity(xml)
                .build();
    }

    private Response buildPropfind(String collection, boolean releasedOnly) {
        TemplateType type = resolveType(collection);
        String basePath = releasedOnly ? "/api/webdav/released/" + collection + "/" : "/api/webdav/" + collection + "/";

        List<Template> templates;
        if (releasedOnly) {
            templates = Template.<Template>find(
                "type = ?1 AND status = ?2 ORDER BY name ASC", type, TemplateStatus.APPROVED
            ).list();
        } else {
            // Latest DRAFT per name
            templates = Template.<Template>find(
                "type = ?1 ORDER BY name ASC, version DESC", type
            ).list().stream()
                .collect(java.util.stream.Collectors.toMap(
                    t -> t.name,
                    t -> t,
                    (existing, newer) -> existing.version > newer.version ? existing : newer
                ))
                .values().stream()
                .sorted((a, b) -> a.name.compareTo(b.name))
                .toList();
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        xml.append("<D:multistatus xmlns:D=\"DAV:\">\n");
        // Entry for the collection itself — required by WebDAV clients (RFC 4918)
        xml.append("  <D:response>\n");
        xml.append("    <D:href>").append(basePath).append("</D:href>\n");
        xml.append("    <D:propstat>\n");
        xml.append("      <D:prop>\n");
        xml.append("        <D:displayname>").append(collection).append("</D:displayname>\n");
        xml.append("        <D:resourcetype><D:collection/></D:resourcetype>\n");
        xml.append("      </D:prop>\n");
        xml.append("      <D:status>HTTP/1.1 200 OK</D:status>\n");
        xml.append("    </D:propstat>\n");
        xml.append("  </D:response>\n");
        for (Template t : templates) {
            long size = t.content != null ? t.content.length : 0;
            xml.append("  <D:response>\n");
            xml.append("    <D:href>").append(basePath).append(t.name).append(".odt</D:href>\n");
            xml.append("    <D:propstat>\n");
            xml.append("      <D:prop>\n");
            xml.append("        <D:displayname>").append(t.name).append(".odt</D:displayname>\n");
            xml.append("        <D:getcontenttype>").append(ODT_CONTENT_TYPE).append("</D:getcontenttype>\n");
            xml.append("        <D:getcontentlength>").append(size).append("</D:getcontentlength>\n");
            xml.append("        <D:resourcetype/>\n");
            xml.append("      </D:prop>\n");
            xml.append("      <D:status>HTTP/1.1 200 OK</D:status>\n");
            xml.append("    </D:propstat>\n");
            xml.append("  </D:response>\n");
        }
        xml.append("</D:multistatus>");

        return Response.status(207)
                .type("application/xml; charset=utf-8")
                .entity(xml.toString())
                .build();
    }
}
