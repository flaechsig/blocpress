package io.github.flaechsig.blocpress.workbench;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct method call tests for TemplateResource.
 * These tests call methods directly (not via HTTP) so JaCoCo can measure them.
 * Uses H2 in-memory database for clean state.
 */
@QuarkusTest
class TemplateResourceDirectMethodTest {

    @Inject
    TemplateResource resource;

    @Inject
    TemplateValidator validator;

    @BeforeEach
    @Transactional
    void setup() {
        // Clear all templates before each test
        Template.deleteAll();
    }

    // ===== list() Tests =====

    @Test
    @Transactional
    void list_EmptyDatabase_ReturnsEmptyList() {
        var result = resource.list();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Transactional
    void list_WithTemplates_ReturnsOnlyLatestVersionPerName() {
        // Create v1 and v2 of same template
        Template t1 = createTemplate("Invoice", 1, TemplateStatus.DRAFT);
        Template t2 = createTemplate("Invoice", 2, TemplateStatus.DRAFT);

        var result = resource.list();

        // Should only return v2 (latest)
        assertEquals(1, result.size());
        assertEquals("Invoice", result.get(0).name());
    }

    @Test
    @Transactional
    void list_WithMultipleTemplates_ReturnsSorted() {
        createTemplate("ZebraTemplate", 1, TemplateStatus.DRAFT);
        createTemplate("AppleTemplate", 1, TemplateStatus.DRAFT);
        createTemplate("BananaTemplate", 1, TemplateStatus.DRAFT);

        var result = resource.list();

        assertEquals(3, result.size());
        // Should be sorted by name
        assertEquals("AppleTemplate", result.get(0).name());
        assertEquals("BananaTemplate", result.get(1).name());
        assertEquals("ZebraTemplate", result.get(2).name());
    }

    // ===== download() Tests =====

    @Test
    @Transactional
    void download_ExistingTemplate_ReturnsContent() {
        Template template = createTemplate("TestTemplate", 1, TemplateStatus.DRAFT);

        var response = resource.download(template.id);

        assertEquals(200, response.getStatus());
        assertArrayEquals(template.content, (byte[]) response.getEntity());
    }

    @Test
    @Transactional
    void download_NonExistentTemplate_Throws404() {
        UUID randomId = UUID.randomUUID();

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.download(randomId);
        });

        assertEquals(404, ex.getResponse().getStatus());
    }

    // ===== getTemplateContent() Tests =====

    @Test
    @Transactional
    void getTemplateContent_ApprovedTemplate_ReturnsContent() {
        Template template = createTemplate("ApprovedTemplate", 1, TemplateStatus.APPROVED);
        template.validFrom = LocalDateTime.now().minusDays(1);
        template.persist();

        var response = resource.getTemplateContent(template.id);

        assertEquals(200, response.getStatus());
    }

    @Test
    @Transactional
    void getTemplateContent_DraftTemplate_Throws403() {
        Template template = createTemplate("DraftTemplate", 1, TemplateStatus.DRAFT);

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.getTemplateContent(template.id);
        });

        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void getTemplateContent_SubmittedTemplate_Throws403() {
        Template template = createTemplate("SubmittedTemplate", 1, TemplateStatus.SUBMITTED);

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.getTemplateContent(template.id);
        });

        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void getTemplateContent_RejectedTemplate_Throws403() {
        Template template = createTemplate("RejectedTemplate", 1, TemplateStatus.REJECTED);

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.getTemplateContent(template.id);
        });

        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void getTemplateContent_NonExistentTemplate_Throws404() {
        UUID randomId = UUID.randomUUID();

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.getTemplateContent(randomId);
        });

        assertEquals(404, ex.getResponse().getStatus());
    }

    // ===== getTemplateContentByName() Tests =====

    @Test
    @Transactional
    void getTemplateContentByName_NonExistentTemplate_Throws404() {
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.getTemplateContentByName("NonExistent");
        });

        assertEquals(404, ex.getResponse().getStatus());
    }

    // ===== getDetails() Tests =====

    @Test
    @Transactional
    void getDetails_ExistingTemplate_ReturnsDetails() {
        Template template = createTemplate("TestTemplate", 1, TemplateStatus.DRAFT);

        var details = resource.getDetails(template.id);

        assertEquals(template.id, details.id());
        assertEquals("TestTemplate", details.name());
        assertEquals(TemplateStatus.DRAFT, details.status());
    }

    @Test
    @Transactional
    void getDetails_NonExistentTemplate_Throws404() {
        UUID randomId = UUID.randomUUID();

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.getDetails(randomId);
        });

        assertEquals(404, ex.getResponse().getStatus());
    }

    // ===== submitForApproval() Tests =====

    @Test
    @Transactional
    void submitForApproval_ValidDraftTemplate_ChangesStatusToSubmitted() {
        Template template = createTemplate("ToSubmit", 1, TemplateStatus.DRAFT);

        var response = resource.submitForApproval(template.id);

        assertEquals(200, response.getStatus());
        Template updated = Template.findById(template.id);
        assertEquals(TemplateStatus.SUBMITTED, updated.status);
    }

    @Test
    @Transactional
    void submitForApproval_NonDraftTemplate_Throws400() {
        Template template = createTemplate("Submitted", 1, TemplateStatus.SUBMITTED);

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.submitForApproval(template.id);
        });

        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void submitForApproval_InvalidTemplate_Throws400() {
        // Create template with invalid result
        Template template = new Template();
        template.name = "Invalid";
        template.version = 1;
        template.content = "test".getBytes();
        template.status = TemplateStatus.DRAFT;
        template.createdAt = Instant.now();
        template.validationResult = new ValidationResult(false, null, java.util.List.of(), java.util.List.of());
        template.persist();

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.submitForApproval(template.id);
        });

        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void submitForApproval_NonExistentTemplate_Throws404() {
        UUID randomId = UUID.randomUUID();

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.submitForApproval(randomId);
        });

        assertEquals(404, ex.getResponse().getStatus());
    }

    // ===== delete() Tests =====

    @Test
    @Transactional
    void delete_ExistingTemplate_DeletesTemplate() {
        Template template = createTemplate("ToDelete", 1, TemplateStatus.DRAFT);
        UUID id = template.id;

        var response = resource.delete(id);

        assertEquals(204, response.getStatus());
        Template deleted = Template.findById(id);
        assertNull(deleted);
    }

    @Test
    @Transactional
    void delete_NonExistentTemplate_Throws404() {
        UUID randomId = UUID.randomUUID();

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.delete(randomId);
        });

        assertEquals(404, ex.getResponse().getStatus());
    }

    // ===== updateStatus() Tests =====

    @Test
    @Transactional
    void updateStatus_ValidTransitionDraftToSubmitted_Changes() {
        Template template = createTemplate("Draft", 1, TemplateStatus.DRAFT);
        var request = new TemplateResource.StatusUpdateRequest(TemplateStatus.SUBMITTED);

        var response = resource.updateStatus(template.id, request);

        assertEquals(200, response.getStatus());
        Template updated = Template.findById(template.id);
        assertEquals(TemplateStatus.SUBMITTED, updated.status);
    }

    @Test
    @Transactional
    void updateStatus_ValidTransitionSubmittedToApproved_SetsValidFrom() {
        Template template = createTemplate("Submitted", 1, TemplateStatus.SUBMITTED);
        var request = new TemplateResource.StatusUpdateRequest(TemplateStatus.APPROVED);

        var response = resource.updateStatus(template.id, request);

        assertEquals(200, response.getStatus());
        Template updated = Template.findById(template.id);
        assertEquals(TemplateStatus.APPROVED, updated.status);
        assertNotNull(updated.validFrom);
    }

    @Test
    @Transactional
    void updateStatus_InvalidTransition_Throws400() {
        Template template = createTemplate("Approved", 1, TemplateStatus.APPROVED);
        var request = new TemplateResource.StatusUpdateRequest(TemplateStatus.DRAFT);

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.updateStatus(template.id, request);
        });

        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void updateStatus_NonExistentTemplate_Throws404() {
        UUID randomId = UUID.randomUUID();
        var request = new TemplateResource.StatusUpdateRequest(TemplateStatus.SUBMITTED);

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.updateStatus(randomId, request);
        });

        assertEquals(404, ex.getResponse().getStatus());
    }

    // ===== TestDataSet Endpoints =====

    @Test
    @Transactional
    void listTestDataSets_NonExistentTemplate_Throws404() {
        UUID randomId = UUID.randomUUID();

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.listTestDataSets(randomId);
        });

        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void getTestDataSet_NonExistentTemplate_Throws404() {
        UUID templateId = UUID.randomUUID();
        UUID testDataSetId = UUID.randomUUID();

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.getTestDataSet(templateId, testDataSetId);
        });

        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void createTestDataSet_NonExistentTemplate_Throws404() {
        UUID templateId = UUID.randomUUID();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var request = new TemplateResource.CreateTestDataSetRequest("TestData", mapper.createObjectNode());

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.createTestDataSet(templateId, request);
        });

        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void updateTestDataSet_NonExistentTemplate_Throws404() {
        UUID templateId = UUID.randomUUID();
        UUID testDataSetId = UUID.randomUUID();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var request = new TemplateResource.CreateTestDataSetRequest("TestData", mapper.createObjectNode());

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.updateTestDataSet(templateId, testDataSetId, request);
        });

        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void deleteTestDataSet_NonExistentTemplate_Throws404() {
        UUID templateId = UUID.randomUUID();
        UUID testDataSetId = UUID.randomUUID();

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.deleteTestDataSet(templateId, testDataSetId);
        });

        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void saveExpectedPdf_NonExistentTemplate_Throws404() {
        UUID templateId = UUID.randomUUID();
        UUID testDataSetId = UUID.randomUUID();
        byte[] pdfContent = "pdf".getBytes();

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.saveExpectedPdf(templateId, testDataSetId, pdfContent);
        });

        assertEquals(404, ex.getResponse().getStatus());
    }

    @Test
    @Transactional
    void getExpectedPdf_NotFound_Throws404() {
        UUID templateId = UUID.randomUUID();
        UUID testDataSetId = UUID.randomUUID();

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.getExpectedPdf(templateId, testDataSetId);
        });

        assertEquals(404, ex.getResponse().getStatus());
    }

    // ===== Helper Methods =====

    private Template createTemplate(String name, int version, TemplateStatus status) {
        Template template = new Template();
        template.name = name;
        template.version = version;
        template.content = "test-content".getBytes();
        template.status = status;
        template.createdAt = Instant.now();
        template.validationResult = new ValidationResult(
            true,
            null,
            java.util.List.of(),
            java.util.List.of()
        );
        template.persist();
        return template;
    }
}
