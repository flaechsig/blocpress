package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
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

@Path("workbench/templates")
@Produces(MediaType.APPLICATION_JSON)
public class TemplateResource {

    @Inject
    TemplateValidator validator;

    @Inject
    TestDataSetService testDataSetService;

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

        byte[] content = Files.readAllBytes(file.uploadedFile());
        ValidationResult validationResult = validator.validate(content);

        Template template = new Template();
        template.name = name.strip();
        template.content = content;
        template.createdAt = Instant.now();
        template.status = TemplateStatus.DRAFT;
        template.validationResult = validationResult;
        template.persist();

        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                    "id", template.id,
                    "name", template.name,
                    "isValid", validationResult.isValid(),
                    "errors", validationResult.errors(),
                    "warnings", validationResult.warnings()
                ))
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

    @GET
    @Path("{id}/details")
    public TemplateDetails getDetails(@PathParam("id") UUID id) {
        Template template = Template.findById(id);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return new TemplateDetails(
            template.id,
            template.name,
            template.createdAt,
            template.status,
            template.validationResult
        );
    }

    @POST
    @Path("{id}/submit")
    @Transactional
    public Response submitForApproval(@PathParam("id") UUID id) {
        Template template = Template.findById(id);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        if (template.status != TemplateStatus.DRAFT) {
            throw new WebApplicationException(
                "Template must be in DRAFT status to submit",
                Response.Status.BAD_REQUEST
            );
        }

        if (template.validationResult == null || !template.validationResult.isValid()) {
            throw new WebApplicationException(
                "Template must be valid to submit",
                Response.Status.BAD_REQUEST
            );
        }

        template.status = TemplateStatus.SUBMITTED;
        template.persist();

        return Response.ok(Map.of(
            "id", template.id,
            "status", template.status
        )).build();
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

    // ===== TestDataSet Endpoints =====

    @GET
    @Path("{templateId}/testdata")
    public List<TestDataSetDTO> listTestDataSets(@PathParam("templateId") UUID templateId) {
        Template template = Template.findById(templateId);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return testDataSetService.listByTemplate(templateId)
            .stream()
            .map(TestDataSetDTO::fromEntity)
            .toList();
    }

    @GET
    @Path("{templateId}/testdata/{testDataSetId}")
    public TestDataSetDTO getTestDataSet(@PathParam("templateId") UUID templateId,
                                         @PathParam("testDataSetId") UUID testDataSetId) {
        Template template = Template.findById(templateId);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        TestDataSet testDataSet = TestDataSet.findById(testDataSetId);
        if (testDataSet == null || !testDataSet.template.id.equals(templateId)) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return TestDataSetDTO.fromEntity(testDataSet);
    }

    @POST
    @Path("{templateId}/testdata")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response createTestDataSet(@PathParam("templateId") UUID templateId,
                                      CreateTestDataSetRequest request) {
        TestDataSet tds = testDataSetService.createTestDataSet(templateId, request.name(), request.testData());
        return Response.status(Response.Status.CREATED)
            .entity(TestDataSetDTO.fromEntity(tds))
            .build();
    }

    @PUT
    @Path("{templateId}/testdata/{testDataSetId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response updateTestDataSet(@PathParam("templateId") UUID templateId,
                                      @PathParam("testDataSetId") UUID testDataSetId,
                                      CreateTestDataSetRequest request) {
        TestDataSet tds = testDataSetService.updateTestDataSet(testDataSetId, request.name(), request.testData());
        return Response.ok(TestDataSetDTO.fromEntity(tds)).build();
    }

    @DELETE
    @Path("{templateId}/testdata/{testDataSetId}")
    @Transactional
    public Response deleteTestDataSet(@PathParam("templateId") UUID templateId,
                                      @PathParam("testDataSetId") UUID testDataSetId) {
        testDataSetService.deleteTestDataSet(testDataSetId);
        return Response.noContent().build();
    }

    @POST
    @Path("{templateId}/testdata/{testDataSetId}/save-expected")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Transactional
    public Response saveExpectedPdf(@PathParam("templateId") UUID templateId,
                                    @PathParam("testDataSetId") UUID testDataSetId,
                                    byte[] pdfContent) {
        testDataSetService.saveExpectedPdf(testDataSetId, pdfContent);
        return Response.ok(Map.of(
            "message", "Expected PDF saved successfully",
            "hash", testDataSetService.calculateHash(pdfContent)
        )).build();
    }

    @GET
    @Path("{templateId}/testdata/{testDataSetId}/expected-pdf")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getExpectedPdf(@PathParam("templateId") UUID templateId,
                                   @PathParam("testDataSetId") UUID testDataSetId) {
        var pdfOptional = testDataSetService.getExpectedPdf(testDataSetId);
        if (pdfOptional.isEmpty()) {
            throw new WebApplicationException("Expected PDF not found", Response.Status.NOT_FOUND);
        }
        return Response.ok(pdfOptional.get())
            .header("Content-Disposition", "attachment; filename=\"expected.pdf\"")
            .build();
    }

    public record TemplateSummary(UUID id, String name, Instant createdAt, TemplateStatus status) {}

    public record TemplateDetails(
        UUID id,
        String name,
        Instant createdAt,
        TemplateStatus status,
        ValidationResult validationResult
    ) {}

    public record CreateTestDataSetRequest(String name, JsonNode testData) {}

    public record TestDataSetDTO(
        UUID id,
        String name,
        JsonNode testData,
        boolean hasExpectedPdf,
        String pdfHash,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static TestDataSetDTO fromEntity(TestDataSet entity) {
            return new TestDataSetDTO(
                entity.id,
                entity.name,
                entity.testData,
                entity.expectedPdf != null,
                entity.pdfHash,
                entity.createdAt,
                entity.updatedAt
            );
        }
    }
}
