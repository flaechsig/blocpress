package io.github.flaechsig.blocpress.workbench;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("templates")
@Produces(MediaType.APPLICATION_JSON)
public class TemplateResource {

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response upload(@RestForm String name, @RestForm("file") FileUpload file) throws IOException {
        if (name == null || name.isBlank()) {
            throw new WebApplicationException("Name is required", Response.Status.BAD_REQUEST);
        }

        if (Template.find("name", name).firstResult() != null) {
            throw new WebApplicationException("Template with name '" + name + "' already exists",
                    Response.Status.CONFLICT);
        }

        Template template = new Template();
        template.name = name.strip();
        template.content = Files.readAllBytes(file.uploadedFile());
        template.createdAt = Instant.now();
        template.persist();

        return Response.status(Response.Status.CREATED)
                .entity(Map.of("id", template.id, "name", template.name))
                .build();
    }

    @GET
    public List<TemplateSummary> list() {
        return Template.find("ORDER BY name")
                .project(TemplateSummary.class)
                .list();
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@PathParam("id") UUID id) {
        Template template = Template.findById(id);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return Response.ok(template.content)
                .header("Content-Disposition", "attachment; filename=\"" + template.name + ".odt\"")
                .build();
    }

    @DELETE
    @Path("{id}")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        Template template = Template.findById(id);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        template.delete();
        return Response.noContent().build();
    }

    public record TemplateSummary(UUID id, String name, Instant createdAt) {}
}
