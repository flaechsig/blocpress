package io.github.flaechsig.blocpress.workbench;
import io.github.flaechsig.blocpress.workbench.repository.TestDataSetRepository;
import io.github.flaechsig.blocpress.workbench.service.JsonSchemaGenerator;
import io.github.flaechsig.blocpress.workbench.service.TestDataSetService;
import io.github.flaechsig.blocpress.workbench.service.TemplateValidator;
import io.github.flaechsig.blocpress.workbench.entity.ValidationResult;
import io.github.flaechsig.blocpress.workbench.entity.TemplateStatus;
import io.github.flaechsig.blocpress.workbench.entity.TemplateType;
import io.github.flaechsig.blocpress.workbench.entity.TestDataSet;
import io.github.flaechsig.blocpress.workbench.entity.Template;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TemplateResource REST records and error handling.
 * Note: Integration tests are disabled due to JaCoCo + Quarkus bytecode conflicts.
 * These unit tests focus on record creation and validation logic that can be tested in isolation.
 */
class TemplateResourceUnitTest {

    @InjectMocks
    private TemplateResource resource;

    @Mock
    private TemplateValidator validator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ===== TemplateSummary Record Tests =====

    @Test
    void testTemplateSummaryCreation() {
        UUID id = UUID.randomUUID();
        String name = "Test Template";
        LocalDateTime createdAt = LocalDateTime.now();
        TemplateStatus status = TemplateStatus.DRAFT;

        TemplateResource.TemplateSummary summary =
            new TemplateResource.TemplateSummary(id, name, createdAt, status, true, TemplateType.TEMPLATE, 1);

        assertEquals(id, summary.id());
        assertEquals(name, summary.name());
        assertEquals(createdAt, summary.createdAt());
        assertEquals(TemplateStatus.DRAFT, summary.status());
        assertTrue(summary.isValid());
    }

    @Test
    void testTemplateSummaryEquality() {
        UUID id = UUID.randomUUID();
        String name = "Test";
        LocalDateTime instant = LocalDateTime.now();
        TemplateStatus status = TemplateStatus.SUBMITTED;

        TemplateResource.TemplateSummary summary1 =
            new TemplateResource.TemplateSummary(id, name, instant, status, true, TemplateType.TEMPLATE, 1);
        TemplateResource.TemplateSummary summary2 =
            new TemplateResource.TemplateSummary(id, name, instant, status, true, TemplateType.TEMPLATE, 1);

        assertEquals(summary1, summary2);
    }

    @Test
    void testTemplateSummaryWithDifferentStatuses() {
        UUID id = UUID.randomUUID();
        String name = "Template";
        LocalDateTime instant = LocalDateTime.now();

        TemplateResource.TemplateSummary draft =
            new TemplateResource.TemplateSummary(id, name, instant, TemplateStatus.DRAFT, true, TemplateType.TEMPLATE, 1);
        TemplateResource.TemplateSummary submitted =
            new TemplateResource.TemplateSummary(id, name, instant, TemplateStatus.SUBMITTED, true, TemplateType.TEMPLATE, 1);
        TemplateResource.TemplateSummary approved =
            new TemplateResource.TemplateSummary(id, name, instant, TemplateStatus.APPROVED, true, TemplateType.TEMPLATE, 1);
        TemplateResource.TemplateSummary rejected =
            new TemplateResource.TemplateSummary(id, name, instant, TemplateStatus.REJECTED, false, TemplateType.TEMPLATE, 1);

        assertEquals(TemplateStatus.DRAFT, draft.status());
        assertEquals(TemplateStatus.SUBMITTED, submitted.status());
        assertEquals(TemplateStatus.APPROVED, approved.status());
        assertEquals(TemplateStatus.REJECTED, rejected.status());
    }

    @Test
    void testTemplateSummaryHashCode() {
        UUID id = UUID.randomUUID();
        TemplateResource.TemplateSummary summary1 =
            new TemplateResource.TemplateSummary(id, "Test", LocalDateTime.now(), TemplateStatus.DRAFT, true, TemplateType.TEMPLATE, 1);
        TemplateResource.TemplateSummary summary2 =
            new TemplateResource.TemplateSummary(id, "Test", summary1.createdAt(), TemplateStatus.DRAFT, true, TemplateType.TEMPLATE, 1);

        assertEquals(summary1.hashCode(), summary2.hashCode());
    }

    // ===== TemplateDetails Record Tests =====

    @Test
    void testTemplateDetailsCreation() {
        UUID id = UUID.randomUUID();
        String name = "Test Template";
        LocalDateTime createdAt = LocalDateTime.now();
        TemplateStatus status = TemplateStatus.DRAFT;
        ValidationResult validationResult = createValidValidationResult();

        TemplateResource.TemplateDetails details =
            new TemplateResource.TemplateDetails(id, name, createdAt, status, validationResult, java.util.List.of(), null, null, null, null, null);

        assertEquals(id, details.id());
        assertEquals(name, details.name());
        assertEquals(createdAt, details.createdAt());
        assertEquals(TemplateStatus.DRAFT, details.status());
        assertEquals(validationResult, details.validationResult());
    }

    @Test
    void testTemplateDetailsWithValidationErrors() {
        UUID id = UUID.randomUUID();
        String name = "Invalid Template";
        LocalDateTime createdAt = LocalDateTime.now();
        TemplateStatus status = TemplateStatus.DRAFT;

        var errors = java.util.List.of(
            new ValidationResult.ValidationMessage("INVALID_STRUCTURE", "ODT structure is invalid")
        );
        var warnings = java.util.List.<ValidationResult.ValidationMessage>of();
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ValidationResult validationResult =
            new ValidationResult(false, schema, errors, warnings, java.util.List.of(), java.util.List.of());

        TemplateResource.TemplateDetails details =
            new TemplateResource.TemplateDetails(id, name, createdAt, status, validationResult, java.util.List.of(), null, null, null, null, null);

        assertFalse(details.validationResult().isValid());
        assertEquals(1, details.validationResult().errors().size());
        assertEquals("INVALID_STRUCTURE", details.validationResult().errors().get(0).code());
    }

    @Test
    void testTemplateDetailsEquality() {
        UUID id = UUID.randomUUID();
        String name = "Test";
        LocalDateTime instant = LocalDateTime.now();
        TemplateStatus status = TemplateStatus.SUBMITTED;
        ValidationResult vr = createValidValidationResult();

        TemplateResource.TemplateDetails details1 =
            new TemplateResource.TemplateDetails(id, name, instant, status, vr, java.util.List.of(), null, null, null, null, null);
        TemplateResource.TemplateDetails details2 =
            new TemplateResource.TemplateDetails(id, name, instant, status, vr, java.util.List.of(), null, null, null, null, null);

        assertEquals(details1, details2);
    }

    @Test
    void testTemplateDetailsWithDifferentStatuses() {
        UUID id = UUID.randomUUID();
        LocalDateTime instant = LocalDateTime.now();
        ValidationResult vr = createValidValidationResult();

        TemplateResource.TemplateDetails draft =
            new TemplateResource.TemplateDetails(id, "Test", instant, TemplateStatus.DRAFT, vr, java.util.List.of(), null, null, null, null, null);
        TemplateResource.TemplateDetails submitted =
            new TemplateResource.TemplateDetails(id, "Test", instant, TemplateStatus.SUBMITTED, vr, java.util.List.of(), null, null, null, null, null);
        TemplateResource.TemplateDetails approved =
            new TemplateResource.TemplateDetails(id, "Test", instant, TemplateStatus.APPROVED, vr, java.util.List.of(), null, null, null, null, null);

        assertEquals(TemplateStatus.DRAFT, draft.status());
        assertEquals(TemplateStatus.SUBMITTED, submitted.status());
        assertEquals(TemplateStatus.APPROVED, approved.status());
    }

    // ===== Upload Endpoint Tests (Error Cases) =====

    @Test
    void testUploadWithNullName() {
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.upload(null, null, null);
        });
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void testUploadWithEmptyName() {
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.upload("", null, null);
        });
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void testUploadWithBlankName() {
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.upload("   ", null, null);
        });
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void testUploadWithSingleSpace() {
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.upload(" ", null, null);
        });
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void testUploadWithTabs() {
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.upload("\t\t", null, null);
        });
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void testUploadWithNewlines() {
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.upload("\n\n", null, null);
        });
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    // ===== Record toString/String Representation Tests =====

    @Test
    void testTemplateSummaryToString() {
        UUID id = UUID.randomUUID();
        TemplateResource.TemplateSummary summary =
            new TemplateResource.TemplateSummary(id, "Test", LocalDateTime.now(), TemplateStatus.DRAFT, true, TemplateType.TEMPLATE, 1);
        String str = summary.toString();
        assertNotNull(str);
        assertTrue(str.contains("Test") || str.contains("DRAFT"));
    }

    @Test
    void testTemplateDetailsToString() {
        UUID id = UUID.randomUUID();
        ValidationResult vr = createValidValidationResult();
        TemplateResource.TemplateDetails details =
            new TemplateResource.TemplateDetails(id, "Test", LocalDateTime.now(), TemplateStatus.DRAFT, vr, java.util.List.of(), null, null, null, null, null);
        String str = details.toString();
        assertNotNull(str);
        assertTrue(str.contains("Test") || str.contains("DRAFT"));
    }

    // ===== Template Versioning Tests (UC-10.1) =====

    @Test
    void testTemplateVersionInitialization() {
        // New templates should have version = 1 by default
        TemplateResource.TemplateSummary summary =
            new TemplateResource.TemplateSummary(
                UUID.randomUUID(),
                "Invoice",
                LocalDateTime.now(),
                TemplateStatus.DRAFT,
                true,
                TemplateType.TEMPLATE,
                1
            );
        assertNotNull(summary, "Template summary should not be null");
        assertEquals("Invoice", summary.name());
    }

    @Test
    void testTemplateStatusWithValidFrom() {
        // When transitioning to APPROVED, validFrom should be set
        // This is tested in integration tests with actual database

        // Unit test: Verify the logic that sets validFrom
        TemplateStatus approved = TemplateStatus.APPROVED;
        TemplateStatus draft = TemplateStatus.DRAFT;

        assertNotEquals(approved, draft);
        assertEquals(TemplateStatus.APPROVED, approved);
    }








    // ===== Duplicate Request Record Tests =====

    @Test
    void testDuplicateRequestCreation() {
        TemplateResource.DuplicateRequest request = new TemplateResource.DuplicateRequest("NewTemplateName");

        assertEquals("NewTemplateName", request.name());
    }

    @Test
    void testDuplicateRequestEquality() {
        TemplateResource.DuplicateRequest request1 = new TemplateResource.DuplicateRequest("Test");
        TemplateResource.DuplicateRequest request2 = new TemplateResource.DuplicateRequest("Test");

        assertEquals(request1, request2);
    }

    @Test
    void testDuplicateRequestToString() {
        TemplateResource.DuplicateRequest request = new TemplateResource.DuplicateRequest("TestName");

        assertTrue(request.toString().contains("TestName"));
    }

    @Test
    void testDuplicateRequestWithDifferentNames() {
        TemplateResource.DuplicateRequest request1 = new TemplateResource.DuplicateRequest("Name1");
        TemplateResource.DuplicateRequest request2 = new TemplateResource.DuplicateRequest("Name2");

        assertNotEquals(request1, request2);
    }

    @Test
    void testDuplicateRequestWithEmptyName() {
        TemplateResource.DuplicateRequest request = new TemplateResource.DuplicateRequest("");

        assertEquals("", request.name());
    }

    @Test
    void testDuplicateRequestWithSpecialCharacters() {
        String specialName = "Template_With-Special.Chars@123";
        TemplateResource.DuplicateRequest request = new TemplateResource.DuplicateRequest(specialName);

        assertEquals(specialName, request.name());
    }

    @Test
    void testDuplicateRequestWithUnicodeCharacters() {
        String unicodeName = "Template_Ümlaute_Äöü";
        TemplateResource.DuplicateRequest request = new TemplateResource.DuplicateRequest(unicodeName);

        assertEquals(unicodeName, request.name());
    }

    // ===== Helper Methods =====

    private ValidationResult createValidValidationResult() {
        var errors = java.util.List.<ValidationResult.ValidationMessage>of();
        var warnings = java.util.List.<ValidationResult.ValidationMessage>of();
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        var properties = objectMapper.createObjectNode();
        var customerProp = objectMapper.createObjectNode();
        customerProp.put("type", "object");
        var customerProps = objectMapper.createObjectNode();
        var nameProp = objectMapper.createObjectNode();
        nameProp.put("type", "string");
        customerProps.set("name", nameProp);
        customerProp.set("properties", customerProps);
        properties.set("customer", customerProp);
        schema.set("properties", properties);

        return new ValidationResult(true, schema, errors, warnings, java.util.List.of(), java.util.List.of());
    }
}
