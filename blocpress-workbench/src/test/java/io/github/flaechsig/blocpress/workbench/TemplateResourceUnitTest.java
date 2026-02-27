package io.github.flaechsig.blocpress.workbench;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
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
        Instant createdAt = Instant.now();
        TemplateStatus status = TemplateStatus.DRAFT;

        TemplateResource.TemplateSummary summary =
            new TemplateResource.TemplateSummary(id, name, createdAt, status);

        assertEquals(id, summary.id());
        assertEquals(name, summary.name());
        assertEquals(createdAt, summary.createdAt());
        assertEquals(TemplateStatus.DRAFT, summary.status());
    }

    @Test
    void testTemplateSummaryEquality() {
        UUID id = UUID.randomUUID();
        String name = "Test";
        Instant instant = Instant.now();
        TemplateStatus status = TemplateStatus.SUBMITTED;

        TemplateResource.TemplateSummary summary1 =
            new TemplateResource.TemplateSummary(id, name, instant, status);
        TemplateResource.TemplateSummary summary2 =
            new TemplateResource.TemplateSummary(id, name, instant, status);

        assertEquals(summary1, summary2);
    }

    @Test
    void testTemplateSummaryWithDifferentStatuses() {
        UUID id = UUID.randomUUID();
        String name = "Template";
        Instant instant = Instant.now();

        TemplateResource.TemplateSummary draft =
            new TemplateResource.TemplateSummary(id, name, instant, TemplateStatus.DRAFT);
        TemplateResource.TemplateSummary submitted =
            new TemplateResource.TemplateSummary(id, name, instant, TemplateStatus.SUBMITTED);
        TemplateResource.TemplateSummary approved =
            new TemplateResource.TemplateSummary(id, name, instant, TemplateStatus.APPROVED);
        TemplateResource.TemplateSummary rejected =
            new TemplateResource.TemplateSummary(id, name, instant, TemplateStatus.REJECTED);

        assertEquals(TemplateStatus.DRAFT, draft.status());
        assertEquals(TemplateStatus.SUBMITTED, submitted.status());
        assertEquals(TemplateStatus.APPROVED, approved.status());
        assertEquals(TemplateStatus.REJECTED, rejected.status());
    }

    @Test
    void testTemplateSummaryHashCode() {
        UUID id = UUID.randomUUID();
        TemplateResource.TemplateSummary summary1 =
            new TemplateResource.TemplateSummary(id, "Test", Instant.now(), TemplateStatus.DRAFT);
        TemplateResource.TemplateSummary summary2 =
            new TemplateResource.TemplateSummary(id, "Test", summary1.createdAt(), TemplateStatus.DRAFT);

        assertEquals(summary1.hashCode(), summary2.hashCode());
    }

    // ===== TemplateDetails Record Tests =====

    @Test
    void testTemplateDetailsCreation() {
        UUID id = UUID.randomUUID();
        String name = "Test Template";
        Instant createdAt = Instant.now();
        TemplateStatus status = TemplateStatus.DRAFT;
        ValidationResult validationResult = createValidValidationResult();

        TemplateResource.TemplateDetails details =
            new TemplateResource.TemplateDetails(id, name, createdAt, status, validationResult);

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
        Instant createdAt = Instant.now();
        TemplateStatus status = TemplateStatus.DRAFT;

        var errors = java.util.List.of(
            new ValidationResult.ValidationMessage("INVALID_STRUCTURE", "ODT structure is invalid")
        );
        var warnings = java.util.List.<ValidationResult.ValidationMessage>of();
        var userFields = java.util.List.<ValidationResult.UserFieldInfo>of();
        var repeatGroups = java.util.List.<ValidationResult.RepetitionGroupInfo>of();
        var conditions = java.util.List.<ValidationResult.ConditionInfo>of();

        ValidationResult validationResult =
            new ValidationResult(false, errors, warnings, userFields, repeatGroups, conditions);

        TemplateResource.TemplateDetails details =
            new TemplateResource.TemplateDetails(id, name, createdAt, status, validationResult);

        assertFalse(details.validationResult().isValid());
        assertEquals(1, details.validationResult().errors().size());
        assertEquals("INVALID_STRUCTURE", details.validationResult().errors().get(0).code());
    }

    @Test
    void testTemplateDetailsEquality() {
        UUID id = UUID.randomUUID();
        String name = "Test";
        Instant instant = Instant.now();
        TemplateStatus status = TemplateStatus.SUBMITTED;
        ValidationResult vr = createValidValidationResult();

        TemplateResource.TemplateDetails details1 =
            new TemplateResource.TemplateDetails(id, name, instant, status, vr);
        TemplateResource.TemplateDetails details2 =
            new TemplateResource.TemplateDetails(id, name, instant, status, vr);

        assertEquals(details1, details2);
    }

    @Test
    void testTemplateDetailsWithDifferentStatuses() {
        UUID id = UUID.randomUUID();
        Instant instant = Instant.now();
        ValidationResult vr = createValidValidationResult();

        TemplateResource.TemplateDetails draft =
            new TemplateResource.TemplateDetails(id, "Test", instant, TemplateStatus.DRAFT, vr);
        TemplateResource.TemplateDetails submitted =
            new TemplateResource.TemplateDetails(id, "Test", instant, TemplateStatus.SUBMITTED, vr);
        TemplateResource.TemplateDetails approved =
            new TemplateResource.TemplateDetails(id, "Test", instant, TemplateStatus.APPROVED, vr);

        assertEquals(TemplateStatus.DRAFT, draft.status());
        assertEquals(TemplateStatus.SUBMITTED, submitted.status());
        assertEquals(TemplateStatus.APPROVED, approved.status());
    }

    // ===== Upload Endpoint Tests (Error Cases) =====

    @Test
    void testUploadWithNullName() {
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.upload(null, null);
        });
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void testUploadWithEmptyName() {
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.upload("", null);
        });
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void testUploadWithBlankName() {
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.upload("   ", null);
        });
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void testUploadWithSingleSpace() {
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.upload(" ", null);
        });
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void testUploadWithTabs() {
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.upload("\t\t", null);
        });
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void testUploadWithNewlines() {
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> {
            resource.upload("\n\n", null);
        });
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    // ===== Record toString/String Representation Tests =====

    @Test
    void testTemplateSummaryToString() {
        UUID id = UUID.randomUUID();
        TemplateResource.TemplateSummary summary =
            new TemplateResource.TemplateSummary(id, "Test", Instant.now(), TemplateStatus.DRAFT);
        String str = summary.toString();
        assertNotNull(str);
        assertTrue(str.contains("Test") || str.contains("DRAFT"));
    }

    @Test
    void testTemplateDetailsToString() {
        UUID id = UUID.randomUUID();
        ValidationResult vr = createValidValidationResult();
        TemplateResource.TemplateDetails details =
            new TemplateResource.TemplateDetails(id, "Test", Instant.now(), TemplateStatus.DRAFT, vr);
        String str = details.toString();
        assertNotNull(str);
        assertTrue(str.contains("Test") || str.contains("DRAFT"));
    }

    // ===== Helper Methods =====

    private ValidationResult createValidValidationResult() {
        var userFields = java.util.List.of(
            new ValidationResult.UserFieldInfo("customer.name", "user-field")
        );
        var repeatGroups = java.util.List.<ValidationResult.RepetitionGroupInfo>of();
        var conditions = java.util.List.<ValidationResult.ConditionInfo>of();
        var errors = java.util.List.<ValidationResult.ValidationMessage>of();
        var warnings = java.util.List.<ValidationResult.ValidationMessage>of();

        return new ValidationResult(true, errors, warnings, userFields, repeatGroups, conditions);
    }
}
