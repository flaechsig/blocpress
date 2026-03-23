package io.github.flaechsig.blocpress.workbench;

import io.github.flaechsig.blocpress.workbench.entity.Template;
import io.github.flaechsig.blocpress.workbench.entity.TemplateStatus;
import io.github.flaechsig.blocpress.workbench.entity.TemplateType;
import io.github.flaechsig.blocpress.workbench.entity.TestDataSet;
import io.github.flaechsig.blocpress.workbench.entity.ValidationResult;
import io.github.flaechsig.blocpress.workbench.service.CoverageAnalysisService;
import io.github.flaechsig.blocpress.workbench.service.ElasticsearchIndexService;
import io.github.flaechsig.blocpress.workbench.service.PdfComparisonService;
import io.github.flaechsig.blocpress.workbench.service.TemplateValidator;
import io.github.flaechsig.blocpress.workbench.service.TestDataSetService;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/workbench/templates")
@Produces(MediaType.APPLICATION_JSON)
public class TemplateResource {

    private static final Logger log = LoggerFactory.getLogger(TemplateResource.class);

    @Inject
    TemplateValidator validator;

    @Inject
    TestDataSetService testDataSetService;

    @Inject
    CoverageAnalysisService coverageAnalysisService;

    @Inject
    PdfComparisonService pdfComparisonService;

    @Inject
    ElasticsearchIndexService elasticsearchIndexService;

    @Inject
    com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @ConfigProperty(name = "quarkus.rest-client.\"render\".url")
    String renderUrl;

    @ConfigProperty(name = "blocpress.compliance.review-lead-days", defaultValue = "60")
    int leadDays;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response upload(@RestForm String name, @RestForm("file") FileUpload file, @RestForm String type) throws IOException {
        if (name == null || name.isBlank()) {
            throw new WebApplicationException("Name is required", Response.Status.BAD_REQUEST);
        }

        TemplateType templateType = "BAUSTEIN".equals(type) ? TemplateType.BAUSTEIN : TemplateType.TEMPLATE;

        byte[] content = Files.readAllBytes(file.uploadedFile());
        ValidationResult validationResult = validator.validate(content);

        // Find the next version number
        Integer nextVersion = 1;
        Template lastVersion = Template.find("name = ?1 AND type = ?2 ORDER BY version DESC", name.strip(), templateType)
                .firstResult();
        if (lastVersion != null) {
            nextVersion = lastVersion.version + 1;
        }

        Template template = new Template();
        template.name = name.strip();
        template.version = nextVersion;
        template.content = content;
        template.createdAt = LocalDateTime.now();
        template.status = TemplateStatus.DRAFT;
        template.type = templateType;
        template.validationResult = validationResult;
        template.persist();
        elasticsearchIndexService.index(template);

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
    public List<TemplateSummary> list(@QueryParam("type") TemplateType type) {
        TemplateType effectiveType = (type != null) ? type : TemplateType.TEMPLATE;
        // Sorted DESC by version so putIfAbsent keeps the highest version per name
        List<Template> all = Template.<Template>find(
            "type = ?1 ORDER BY name ASC, version DESC", effectiveType
        ).list();

        // For each name: latest non-APPROVED (in-progress) + latest APPROVED separately
        java.util.Map<String, Template> approvedByName = new java.util.LinkedHashMap<>();
        java.util.Map<String, Template> inProgressByName = new java.util.LinkedHashMap<>();
        for (Template t : all) {
            if (t.status == TemplateStatus.APPROVED) {
                approvedByName.putIfAbsent(t.name, t);
            } else {
                inProgressByName.putIfAbsent(t.name, t);
            }
        }

        // Merge: in-progress first, then approved, unique names in sorted order
        java.util.Set<String> allNames = new java.util.LinkedHashSet<>(inProgressByName.keySet());
        allNames.addAll(approvedByName.keySet());

        List<TemplateSummary> result = new java.util.ArrayList<>();
        for (String name : allNames) {
            Template inProgress = inProgressByName.get(name);
            Template approved = approvedByName.get(name);
            if (inProgress != null) result.add(toSummary(inProgress));
            if (approved != null) result.add(toSummary(approved));
        }
        return result;
    }

    private TemplateSummary toSummary(Template t) {
        return new TemplateSummary(t.id, t.name, t.createdAt, t.status,
            t.validationResult != null && t.validationResult.isValid(), t.type, t.version);
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
            template.validationResult,
            template.ignoredPatterns != null ? template.ignoredPatterns : List.of(),
            template.rejectionReason,
            template.rejectedAt,
            template.validFrom,
            template.validUntil,
            template.reviewCycleYears
        );
    }

    @POST
    @Path("{id}/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response previewTemplate(@PathParam("id") UUID id, PreviewRequest request) {
        Template template = Template.findById(id);
        if (template == null || template.content == null || template.content.length == 0) {
            throw new WebApplicationException("Template not found or empty", Response.Status.NOT_FOUND);
        }

        try {
            // Build request JSON manually for reliable serialization
            String base64Template = Base64.getEncoder().encodeToString(template.content);
            com.fasterxml.jackson.databind.node.ObjectNode requestJson = objectMapper.createObjectNode();
            requestJson.put("template", base64Template);
            requestJson.set("data", objectMapper.valueToTree(request.data()));
            requestJson.put("outputType", request.outputType());

            String requestBody = objectMapper.writeValueAsString(requestJson);

            // Make HTTP request using standard Java HttpClient
            HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(renderUrl + "/render/template"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/pdf")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<byte[]> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

            if (httpResponse.statusCode() >= 400) {
                String errorMsg = new String(httpResponse.body());
                log.error("Render service returned {} for template {}: {}", httpResponse.statusCode(), id, errorMsg);
                throw new WebApplicationException(Response.status(Response.Status.BAD_GATEWAY)
                    .entity(Map.of("error", "Render service error (" + httpResponse.statusCode() + "): " + errorMsg))
                    .type(MediaType.APPLICATION_JSON)
                    .build());
            }

            String contentType = httpResponse.headers().firstValue("Content-Type").orElse("application/pdf");
            if (contentType.isBlank()) {
                contentType = "application/pdf";
            }

            return Response.status(httpResponse.statusCode())
                .type(contentType)
                .entity(httpResponse.body())
                .build();
        } catch (WebApplicationException e) {
            throw e;  // Re-throw as-is
        } catch (Exception e) {
            log.error("Preview failed for template {}: {}", id, e.getMessage(), e);
            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Failed to render preview: " + e.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build());
        }
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
        // Clear rejection info when resubmitting
        template.rejectionReason = null;
        template.rejectedAt = null;
        template.persist();

        return Response.ok(Map.of(
            "id", template.id,
            "status", template.status
        )).build();
    }

    @POST
    @Path("{id}/reject")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response reject(@PathParam("id") UUID id, RejectRequest request) {
        Template template = Template.findById(id);
        if (template == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
        if (template.status != TemplateStatus.SUBMITTED) {
            throw new WebApplicationException(
                "Template must be in SUBMITTED status to reject. Current: " + template.status,
                Response.Status.BAD_REQUEST
            );
        }
        template.status = TemplateStatus.DRAFT;
        template.rejectionReason = request.reason();
        template.rejectedAt = LocalDateTime.now();
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
        elasticsearchIndexService.delete(template.id);
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

        if (request.newStatus() == TemplateStatus.APPROVED) {
            LocalDateTime from = request.validFrom() != null
                ? request.validFrom().atStartOfDay()
                : (template.validFrom != null ? template.validFrom : LocalDateTime.now());
            template.validFrom = from;
            template.reviewCycleYears = request.reviewCycleYears();
            template.validUntil = request.reviewCycleYears() != null
                ? from.plusYears(request.reviewCycleYears())
                : null;
        }

        if (request.newStatus() == TemplateStatus.RETIRED) {
            template.validUntil = LocalDateTime.now();
        }

        template.persist();
        elasticsearchIndexService.updateStatus(template.id, template.status.name());

        // TI-2: Auto-deploy to production when transitioning to APPROVED
        if (request.newStatus() == TemplateStatus.APPROVED) {
            try {
                // Build deploy request JSON
                com.fasterxml.jackson.databind.node.ObjectNode deployJson = objectMapper.createObjectNode();
                deployJson.put("id", template.id.toString());
                deployJson.put("name", template.name);
                deployJson.put("version", template.version);
                deployJson.put("contentBase64", Base64.getEncoder().encodeToString(template.content));
                deployJson.put("validFrom", template.validFrom.toString());
                if (template.validUntil != null) {
                    deployJson.put("validUntil", template.validUntil.toString());
                }

                String deployBody = objectMapper.writeValueAsString(deployJson);

                // Deploy to production via HTTP
                HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
                HttpRequest deployRequest = HttpRequest.newBuilder()
                    .uri(URI.create(renderUrl + "/render/templates/import"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(deployBody))
                    .build();

                HttpResponse<String> deployResponse = httpClient.send(deployRequest, HttpResponse.BodyHandlers.ofString());
                if (deployResponse.statusCode() >= 400) {
                    throw new Exception("Deploy failed with status " + deployResponse.statusCode() + ": " + deployResponse.body());
                }
            } catch (Exception e) {
                // Rollback the status change since deploy failed
                throw new WebApplicationException(
                    "Template approved but deploy to production failed: " + e.getMessage(),
                    Response.Status.SERVICE_UNAVAILABLE
                );
            }
        }

        // UC-12: RETIRED — remove from Elasticsearch and production DB
        if (request.newStatus() == TemplateStatus.RETIRED) {
            try {
                elasticsearchIndexService.delete(template.id);
            } catch (Exception e) {
                log.warn("Could not remove template '{}' from Elasticsearch: {}", template.name, e.getMessage());
            }
            removeFromProduction(template.name);
        }

        var responseMap = new java.util.HashMap<String, Object>();
        responseMap.put("id", template.id);
        responseMap.put("status", template.status);
        responseMap.put("validFrom", template.validFrom);
        if (template.validUntil != null) {
            responseMap.put("validUntil", template.validUntil);
        }

        return Response.ok(responseMap).build();
    }

    /**
     * UC-12: Returns all APPROVED templates whose validUntil is within the lead-day window.
     */
    @GET
    @Path("/due-for-review")
    public List<TemplateDetails> getDueForReview() {
        LocalDateTime threshold = LocalDateTime.now().plusDays(leadDays);
        return Template.<Template>list(
                "status = 'APPROVED' AND validUntil IS NOT NULL AND validUntil <= ?1", threshold)
            .stream()
            .map(t -> new TemplateDetails(t.id, t.name, t.createdAt, t.status,
                t.validationResult, t.ignoredPatterns, t.rejectionReason, t.rejectedAt,
                t.validFrom, t.validUntil, t.reviewCycleYears))
            .toList();
    }

    private void removeFromProduction(String templateName) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(renderUrl + "/render/templates/import/" + templateName))
                .DELETE()
                .timeout(Duration.ofSeconds(30))
                .build();
            HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() >= 400) {
                log.warn("Could not remove template '{}' from production: HTTP {}", templateName, resp.statusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to call render DELETE for template '{}': {}", templateName, e.getMessage());
        }
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
        duplicate.createdAt = LocalDateTime.now();

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
            case APPROVED -> to == TemplateStatus.SUBMITTED || to == TemplateStatus.RETIRED;
            case REJECTED -> to == TemplateStatus.DRAFT;
            case RETIRED -> false;
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
        // Validate template exists first
        Template template = Template.findById(templateId);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
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
        // Validate template exists first
        Template template = Template.findById(templateId);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        TestDataSet tds = testDataSetService.updateTestDataSet(testDataSetId, request.name(), request.testData());
        return Response.ok(TestDataSetDTO.fromEntity(tds)).build();
    }

    @DELETE
    @Path("{templateId}/testdata/{testDataSetId}")
    @Transactional
    public Response deleteTestDataSet(@PathParam("templateId") UUID templateId,
                                      @PathParam("testDataSetId") UUID testDataSetId) {
        // Validate template exists first
        Template template = Template.findById(templateId);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
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
        // Validate template exists first
        Template template = Template.findById(templateId);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
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

    // ===== Coverage & Regression Endpoints =====

    @GET
    @Path("{id}/coverage")
    public CoverageAnalysisService.CoverageReport getCoverage(@PathParam("id") UUID id) {
        Template template = Template.findById(id);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        return coverageAnalysisService.analyze(id);
    }

    @POST
    @Path("{templateId}/testdata/{testDataSetId}/run-regression")
    @Transactional
    public RegressionResult runRegression(@PathParam("templateId") UUID templateId,
                                          @PathParam("testDataSetId") UUID testDataSetId) {
        Template template = Template.findById(templateId);
        if (template == null || template.content == null || template.content.length == 0) {
            throw new WebApplicationException("Template not found or empty", Response.Status.NOT_FOUND);
        }
        TestDataSet tds = TestDataSet.findById(testDataSetId);
        if (tds == null || !tds.template.id.equals(templateId)) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        if (tds.expectedPdf == null || tds.pdfHash == null) {
            return new RegressionResult(testDataSetId, tds.name, false, false, false, null);
        }

        try {
            byte[] actualPdf = renderPdf(template, tds);
            List<String> ignoredPatterns = mergedIgnoredPatterns(template, tds);
            boolean identicalWithoutIgnoring = pdfComparisonService.areVisuallyIdentical(tds.expectedPdf, actualPdf, List.of());
            boolean passedWithIgnoring = identicalWithoutIgnoring
                || pdfComparisonService.areVisuallyIdentical(tds.expectedPdf, actualPdf, ignoredPatterns);
            boolean hasAcceptedDeviations = passedWithIgnoring && !identicalWithoutIgnoring;
            return new RegressionResult(testDataSetId, tds.name, true, passedWithIgnoring, hasAcceptedDeviations, null);
        } catch (Exception e) {
            return new RegressionResult(testDataSetId, tds.name, true, false, false,
                "Fehler: " + e.getMessage());
        }
    }

    @POST
    @Path("{templateId}/testdata/{testDataSetId}/regression-diff")
    @Produces("application/pdf")
    public Response regressionDiff(@PathParam("templateId") UUID templateId,
                                   @PathParam("testDataSetId") UUID testDataSetId) {
        Template template = Template.findById(templateId);
        if (template == null || template.content == null || template.content.length == 0) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        TestDataSet tds = TestDataSet.findById(testDataSetId);
        if (tds == null || !tds.template.id.equals(templateId)) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        if (tds.expectedPdf == null) {
            throw new WebApplicationException("Kein Expected PDF gespeichert", Response.Status.BAD_REQUEST);
        }

        try {
            byte[] actualPdf = renderPdf(template, tds);
            byte[] diffPdf = pdfComparisonService.generateDiffPdf(tds.expectedPdf, actualPdf);
            if (diffPdf == null)
                throw new WebApplicationException("Diff-Generierung fehlgeschlagen", Response.Status.INTERNAL_SERVER_ERROR);
            return Response.ok(diffPdf)
                .header("Content-Disposition", "inline; filename=\"diff-" + tds.name + ".pdf\"")
                .build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException("Fehler: " + e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Renders the template with the TestDataSet's data and saves the result as the new expected PDF.
     * Replaces the browser-side render approach (which fails due to CORS/routing).
     */
    @POST
    @Path("{templateId}/testdata/{testDataSetId}/save-rendered-as-expected")
    @Transactional
    public Response saveRenderedAsExpected(@PathParam("templateId") UUID templateId,
                                           @PathParam("testDataSetId") UUID testDataSetId) {
        Template template = Template.findById(templateId);
        if (template == null || template.content == null || template.content.length == 0)
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        TestDataSet tds = TestDataSet.findById(testDataSetId);
        if (tds == null || !tds.template.id.equals(templateId))
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        try {
            byte[] pdf = renderPdf(template, tds);
            testDataSetService.saveExpectedPdf(testDataSetId, pdf);
            return Response.ok().build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException("Render-Fehler: " + e.getMessage(),
                Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST
    @Path("{id}/run-all-regressions")
    @Transactional
    public List<RegressionResult> runAllRegressions(@PathParam("id") UUID id) {
        Template template = Template.findById(id);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        List<TestDataSet> testDataSets = TestDataSet.list("template.id", id);
        List<RegressionResult> results = new java.util.ArrayList<>();
        for (TestDataSet tds : testDataSets) {
            results.add(runRegression(id, tds.id));
        }
        return results;
    }

    @POST
    @Path("{id}/testdata/from-suggestion")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response createFromSuggestion(@PathParam("id") UUID id,
                                         CoverageAnalysisService.TestDataSuggestion suggestion) {
        Template template = Template.findById(id);
        if (template == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        TestDataSet tds = testDataSetService.createTestDataSet(id, suggestion.suggestedName(), suggestion.suggestedData());
        return Response.status(Response.Status.CREATED)
            .entity(TestDataSetDTO.fromEntity(tds))
            .build();
    }

    // ===== Inline Diff Pages =====

    @POST
    @Path("{templateId}/testdata/{testDataSetId}/regression-diff-pages")
    public Response regressionDiffPages(@PathParam("templateId") UUID templateId,
                                        @PathParam("testDataSetId") UUID testDataSetId) {
        Template template = Template.findById(templateId);
        if (template == null || template.content == null || template.content.length == 0)
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        TestDataSet tds = TestDataSet.findById(testDataSetId);
        if (tds == null || !tds.template.id.equals(templateId))
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        if (tds.expectedPdf == null)
            throw new WebApplicationException("Kein Expected PDF gespeichert", Response.Status.BAD_REQUEST);

        try {
            byte[] actualPdf = renderPdf(template, tds);
            List<String> ignoredPatterns = mergedIgnoredPatterns(template, tds);
            PdfComparisonService.DiffPagesReport report =
                pdfComparisonService.generateDiffPages(tds.expectedPdf, actualPdf, ignoredPatterns);
            if (report == null)
                throw new WebApplicationException("Diff-Generierung fehlgeschlagen", Response.Status.INTERNAL_SERVER_ERROR);
            return Response.ok(report).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException("Fehler: " + e.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    // ===== Ignored Patterns =====

    @PUT
    @Path("{id}/ignored-patterns")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response updateIgnoredPatterns(@PathParam("id") UUID id, IgnoredPatternsRequest request) {
        Template template = Template.findById(id);
        if (template == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
        template.ignoredPatterns = request.patterns() != null ? request.patterns() : List.of();
        template.persist();
        return Response.ok().build();
    }

    // ===== Notes (per TestDataSet) =====

    @PUT
    @Path("{templateId}/testdata/{testDataSetId}/notes")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response updateNotes(@PathParam("templateId") UUID templateId,
                                @PathParam("testDataSetId") UUID testDataSetId,
                                NotesRequest request) {
        TestDataSet tds = TestDataSet.findById(testDataSetId);
        if (tds == null || !tds.template.id.equals(templateId))
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        tds.notes = request.notes();
        tds.updatedAt = java.time.Instant.now();
        tds.persist();
        return Response.ok().build();
    }

    // ===== New Draft from failed regression =====

    @POST
    @Path("{id}/new-draft")
    @Transactional
    public Response createNewDraft(@PathParam("id") UUID id) {
        Template current = Template.findById(id);
        if (current == null) throw new WebApplicationException(Response.Status.NOT_FOUND);

        // Find highest version for this template name
        Object maxVersionObj = Template.find(
            "SELECT MAX(t.version) FROM Template t WHERE t.name = ?1", current.name
        ).project(Integer.class).firstResult();
        int nextVersion = (maxVersionObj instanceof Integer mv ? mv : current.version) + 1;

        Template draft = new Template();
        draft.name = current.name;
        draft.version = nextVersion;
        draft.content = current.content;
        draft.status = TemplateStatus.DRAFT;
        draft.type = current.type;
        draft.validFrom = LocalDateTime.now();
        draft.ignoredPatterns = current.ignoredPatterns != null ? new java.util.ArrayList<>(current.ignoredPatterns) : new java.util.ArrayList<>();
        draft.persist();

        // Copy all TestDataSets (including expectedPdf) to the new draft
        List<TestDataSet> existingTds = TestDataSet.list("template.id", current.id);
        for (TestDataSet src : existingTds) {
            TestDataSet copy = new TestDataSet();
            copy.template = draft;
            copy.name = src.name;
            copy.testData = src.testData;
            copy.expectedPdf = src.expectedPdf;
            copy.pdfHash = src.pdfHash;
            copy.notes = src.notes;
            copy.persist();
        }

        return Response.status(Response.Status.CREATED)
            .entity(new TemplateSummary(draft.id, draft.name, draft.createdAt,
                draft.status, false, draft.type, draft.version))
            .build();
    }

    // ===== Helper =====

    /** Kombiniert Template-weite und TestDataSet-eigene Ignore-Patterns. */
    private List<String> mergedIgnoredPatterns(Template template, TestDataSet tds) {
        List<String> merged = new java.util.ArrayList<>();
        if (template.ignoredPatterns != null) merged.addAll(template.ignoredPatterns);
        if (tds != null && tds.ignoredPatterns != null) merged.addAll(tds.ignoredPatterns);
        return merged;
    }

    /**
     * Fügt ein Ignore-Pattern hinzu — entweder für den einzelnen TestDataSet oder für alle (Template-Ebene).
     */
    @POST
    @Path("{templateId}/testdata/{testDataSetId}/ignore-block")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response ignoreBlock(@PathParam("templateId") UUID templateId,
                                @PathParam("testDataSetId") UUID testDataSetId,
                                IgnoreBlockRequest request) {
        Template template = Template.findById(templateId);
        if (template == null) throw new WebApplicationException(Response.Status.NOT_FOUND);
        TestDataSet tds = TestDataSet.findById(testDataSetId);
        if (tds == null || !tds.template.id.equals(templateId))
            throw new WebApplicationException(Response.Status.NOT_FOUND);

        String pattern = request.pattern();
        if (pattern == null || pattern.isBlank())
            throw new WebApplicationException("Pattern darf nicht leer sein", Response.Status.BAD_REQUEST);

        if (request.scope().equals("all")) {
            if (template.ignoredPatterns == null) template.ignoredPatterns = new java.util.ArrayList<>();
            if (!template.ignoredPatterns.contains(pattern)) template.ignoredPatterns.add(pattern);
            template.persist();
        } else {
            if (tds.ignoredPatterns == null) tds.ignoredPatterns = new java.util.ArrayList<>();
            if (!tds.ignoredPatterns.contains(pattern)) tds.ignoredPatterns.add(pattern);
            tds.updatedAt = java.time.Instant.now();
            tds.persist();
        }
        return Response.ok().build();
    }

    public record IgnoreBlockRequest(String pattern, String scope) {} // scope: "this" | "all"

    private byte[] renderPdf(Template template, TestDataSet tds) throws Exception {
        String base64Template = Base64.getEncoder().encodeToString(template.content);
        com.fasterxml.jackson.databind.node.ObjectNode requestJson = objectMapper.createObjectNode();
        requestJson.put("template", base64Template);
        requestJson.set("data", tds.testData);
        requestJson.put("outputType", "pdf");
        String requestBody = objectMapper.writeValueAsString(requestJson);

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(renderUrl + "/render/template"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/pdf")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        HttpResponse<byte[]> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (httpResponse.statusCode() >= 400) {
            log.error("Render-Fehler bei renderPdf für template {}: {} — {}", template.id, httpResponse.statusCode(), new String(httpResponse.body()));
            throw new WebApplicationException("Render-Fehler: " + httpResponse.statusCode(), Response.Status.INTERNAL_SERVER_ERROR);
        }
        return httpResponse.body();
    }

    public record RegressionResult(
        UUID testDataSetId,
        String testDataSetName,
        boolean hasExpectedPdf,
        boolean passed,
        boolean hasAcceptedDeviations,  // passed=true, aber nur wegen ignorierten Abweichungen
        String errorMessage
    ) {}

    public record IgnoredPatternsRequest(List<String> patterns) {}
    public record NotesRequest(String notes) {}

    public record TemplateSummary(UUID id, String name, LocalDateTime createdAt, TemplateStatus status, boolean isValid, TemplateType type, Integer version) {}

    public record TemplateDetails(
        UUID id,
        String name,
        LocalDateTime createdAt,
        TemplateStatus status,
        ValidationResult validationResult,
        List<String> ignoredPatterns,
        String rejectionReason,
        LocalDateTime rejectedAt,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        Integer reviewCycleYears
    ) {}

    public record StatusUpdateRequest(
        TemplateStatus newStatus,
        LocalDate validFrom,          // optional, nur bei APPROVED relevant
        Integer reviewCycleYears      // optional, null = kein Ablauf
    ) {}
    public record RejectRequest(String reason) {}

    public record DuplicateRequest(String name) {}

    public record PreviewRequest(Object data, String outputType) {}

    public record CreateTestDataSetRequest(String name, JsonNode testData) {}

    public record TestDataSetDTO(
        UUID id,
        String name,
        JsonNode testData,
        boolean hasExpectedPdf,
        String pdfHash,
        Instant createdAt,
        Instant updatedAt,
        String notes
    ) {
        public static TestDataSetDTO fromEntity(TestDataSet entity) {
            return new TestDataSetDTO(
                entity.id,
                entity.name,
                entity.testData,
                entity.expectedPdf != null,
                entity.pdfHash,
                entity.createdAt,
                entity.updatedAt,
                entity.notes
            );
        }
    }
}
