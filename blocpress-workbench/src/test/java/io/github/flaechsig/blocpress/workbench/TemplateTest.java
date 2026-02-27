package io.github.flaechsig.blocpress.workbench;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Template entity.
 * Note: Persistence operations require @QuarkusTest with database setup.
 * Disabled for integration test runs due to JPA initialization issues with TestContainers.
 */
@Disabled("Disabled for integration test runs - causes StackOverflow with Quarkus TestContainers")
class TemplateTest {

    @Test
    void testTemplateCreation() {
        Template template = new Template();
        UUID id = UUID.randomUUID();

        template.id = id;
        template.name = "Test Template";
        Instant now = Instant.now();
        template.createdAt = now;
        template.status = TemplateStatus.DRAFT;
        template.content = new byte[]{1, 2, 3};

        assertEquals(id, template.id);
        assertEquals("Test Template", template.name);
        assertEquals(now, template.createdAt);
        assertEquals(TemplateStatus.DRAFT, template.status);
        assertArrayEquals(new byte[]{1, 2, 3}, template.content);
    }

    @Test
    void testTemplateDefaultStatus() {
        Template template = new Template();
        assertEquals(TemplateStatus.DRAFT, template.status);
    }

    @Test
    void testTemplateStatusChanges() {
        Template template = new Template();

        assertEquals(TemplateStatus.DRAFT, template.status);

        template.status = TemplateStatus.SUBMITTED;
        assertEquals(TemplateStatus.SUBMITTED, template.status);

        template.status = TemplateStatus.APPROVED;
        assertEquals(TemplateStatus.APPROVED, template.status);

        template.status = TemplateStatus.REJECTED;
        assertEquals(TemplateStatus.REJECTED, template.status);
    }

    @Test
    void testTemplateContentStorage() {
        Template template = new Template();
        byte[] testContent = "This is test ODT content".getBytes();

        template.content = testContent;

        assertNotNull(template.content);
        assertArrayEquals(testContent, template.content);
        assertEquals(testContent.length, template.content.length);
    }

    @Test
    void testTemplateValidationResult() {
        Template template = new Template();
        var errors = java.util.List.<ValidationResult.ValidationMessage>of();
        var warnings = java.util.List.<ValidationResult.ValidationMessage>of();
        var userFields = java.util.List.<ValidationResult.UserFieldInfo>of();
        var repeatGroups = java.util.List.<ValidationResult.RepetitionGroupInfo>of();
        var conditions = java.util.List.<ValidationResult.ConditionInfo>of();

        ValidationResult result = new ValidationResult(true, errors, warnings, userFields, repeatGroups, conditions);
        template.validationResult = result;

        assertNotNull(template.validationResult);
        assertTrue(template.validationResult.isValid());
    }

    @Test
    void testTemplateMetadata() {
        Template template = new Template();
        UUID id = UUID.randomUUID();
        String name = "MyTemplate";
        Instant created = Instant.parse("2025-02-27T10:00:00Z");

        template.id = id;
        template.name = name;
        template.createdAt = created;

        assertEquals(id, template.id);
        assertEquals(name, template.name);
        assertEquals(created, template.createdAt);
        assertNotNull(template.createdAt);
    }
}
