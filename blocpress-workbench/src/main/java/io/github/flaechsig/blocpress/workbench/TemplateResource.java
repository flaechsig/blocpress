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
import java.time.LocalDateTime;
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

        byte[] content = Files.readAllBytes(file.uploadedFile());
        ValidationResult validationResult = validator.validate(content);

        // Find the next version number
        Integer nextVersion = 1;
        Template lastVersion = Template.find("name = ?1 ORDER BY version DESC", name.strip())
                .firstResult();
        if (lastVersion != null) {
            nextVersion = lastVersion.version + 1;
        }

        Template template = new Template();
        template.name = name.strip();
        template.version = nextVersion;
        template.content = content;
        template.createdAt = Instant.now();
        template.status = TemplateStatus.DRAFT;
        template.validationResult = validationResult;
        template.persist();

        return Response.status(Response.Status.CREATED)
                .entity(Map.of(
                    "id", template.id,
                    "name", template.name,
                    "version", template.version,
                    "isValid", validationResult.isValid(),
                    "errors", validationResult.errors(),
                    "warnings", validationResult.warnings()
                ))
                .build();
    }

    @GET
    public List<TemplateSummary> list() {
        return Template.<Template>find("ORDER BY name").list()
                .stream()
                .map(t -> new TemplateSummary(
                    t.id,
                    t.name,
                    t.createdAt,
                    t.status,
                    t.validationResult != null && t.validationResult.isValid()
                ))
                .toList();
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
    @Path("{id}/content")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getTemplateContent(@PathParam("id") UUID id) {
        Template template = Template.findById(id);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        // Only APPROVED templates can be used for production rendering
        if (template.status != TemplateStatus.APPROVED) {
            throw new WebApplicationException(
                "Template must be APPROVED for rendering. Current status: " + template.status,
                Response.Status.FORBIDDEN
            );
        }

        // Return template binary content with proper headers
        return Response.ok(template.content)
                .header("Content-Disposition", "attachment; filename=\"" + template.name + ".odt\"")
                .header("X-Template-Name", template.name)
                .header("X-Template-Status", template.status.toString())
                .header("X-Template-Version", template.version.toString())
                .build();
    }

    @GET
    @Path("by-name/{name}/content")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getTemplateContentByName(@PathParam("name") String name) {
        // Find the latest active version (validFrom <= now and APPROVED)
        Template template = Template.findLatestActiveByName(name);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        // Return template binary content with proper headers
        return Response.ok(template.content)
                .header("Content-Disposition", "attachment; filename=\"" + template.name + ".odt\"")
                .header("X-Template-Name", template.name)
                .header("X-Template-Version", template.version.toString())
                .header("X-Template-ValidFrom", template.validFrom != null ? template.validFrom.toString() : "N/A")
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

    @PUT
    @Path("{id}/status")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response updateStatus(@PathParam("id") UUID id, StatusUpdateRequest request) {
        Template template = Template.findById(id);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        // Validate status transition
        if (!isValidTransition(template.status, request.newStatus())) {
            throw new WebApplicationException(
                "Invalid status transition: " + template.status + " -> " + request.newStatus(),
                Response.Status.BAD_REQUEST
            );
        }

        template.status = request.newStatus();

        // Set validFrom when transitioning to APPROVED
        if (request.newStatus() == TemplateStatus.APPROVED && template.validFrom == null) {
            template.validFrom = LocalDateTime.now();
        }

        template.persist();

        var responseMap = new java.util.HashMap<String, Object>();
        responseMap.put("id", template.id);
        responseMap.put("status", template.status);
        if (template.validFrom != null) {
            responseMap.put("validFrom", template.validFrom);
        }

        return Response.ok(responseMap).build();
    }

    @POST
    @Path("{id}/duplicate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response duplicate(@PathParam("id") UUID id, DuplicateRequest request) throws IOException {
        Template source = Template.findById(id);
        if (source == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        // Determine version for duplicate based on target name
        String targetName = request.name();
        Integer targetVersion = 1;

        // If duplicating with same name, auto-increment version (like upload)
        if (targetName.equals(source.name)) {
            Template lastVersion = Template.find("name = ?1 ORDER BY version DESC", targetName)
                    .firstResult();
            if (lastVersion != null) {
                targetVersion = lastVersion.version + 1;
            }
        } else {
            // If using different name, check that it doesn't already exist
            Template existing = Template.find("name = ?1", targetName).firstResult();
            if (existing != null) {
                throw new WebApplicationException(
                    "Template with name '" + targetName + "' already exists",
                    Response.Status.CONFLICT
                );
            }
        }

        // Create new template as DRAFT
        Template duplicate = new Template();
        duplicate.name = targetName;
        duplicate.version = targetVersion;
        duplicate.content = source.content.clone(); // Copy binary content
        duplicate.status = TemplateStatus.DRAFT;
        duplicate.createdAt = Instant.now();

        // Re-validate (might have different results due to changes in validator)
        ValidationResult validationResult = validator.validate(duplicate.content);
        duplicate.validationResult = validationResult;

        duplicate.persist();

        return Response.status(Response.Status.CREATED)
            .entity(Map.of(
                "id", duplicate.id,
                "name", duplicate.name,
                "version", duplicate.version,
                "status", duplicate.status,
                "isValid", validationResult.isValid()
            ))
            .build();
    }

    @PUT
    @Path("{id}/content")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response updateContent(@PathParam("id") UUID id, @RestForm("file") FileUpload file) throws IOException {
        Template template = Template.findById(id);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        // Only DRAFT templates can be updated
        if (template.status != TemplateStatus.DRAFT) {
            throw new WebApplicationException(
                "Only DRAFT templates can be updated. Current status: " + template.status,
                Response.Status.BAD_REQUEST
            );
        }

        // Update content and re-validate
        byte[] content = Files.readAllBytes(file.uploadedFile());
        ValidationResult validationResult = validator.validate(content);

        template.content = content;
        template.validationResult = validationResult;
        template.persist();

        return Response.ok(Map.of(
            "id", template.id,
            "name", template.name,
            "status", template.status,
            "isValid", validationResult.isValid(),
            "errors", validationResult.errors(),
            "warnings", validationResult.warnings()
        )).build();
    }

    private boolean isValidTransition(TemplateStatus from, TemplateStatus to) {
        return switch (from) {
            case DRAFT -> to == TemplateStatus.SUBMITTED;
            case SUBMITTED -> to == TemplateStatus.DRAFT || to == TemplateStatus.APPROVED || to == TemplateStatus.REJECTED;
            case APPROVED -> to == TemplateStatus.SUBMITTED;
            case REJECTED -> to == TemplateStatus.DRAFT;
        };
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

    public record TemplateSummary(UUID id, String name, Instant createdAt, TemplateStatus status, boolean isValid) {}

    public record TemplateDetails(
        UUID id,
        String name,
        Instant createdAt,
        TemplateStatus status,
        ValidationResult validationResult
    ) {}

    public record StatusUpdateRequest(TemplateStatus newStatus) {}

    public record DuplicateRequest(String name) {}

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
