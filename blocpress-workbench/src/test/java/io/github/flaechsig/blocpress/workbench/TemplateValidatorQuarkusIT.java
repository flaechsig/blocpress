package io.github.flaechsig.blocpress.workbench;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Quarkus integration tests for TemplateValidator.
 * Tests the validator with actual Quarkus CDI injection.
 *
 * Note: This is an integration test (suffix IT) and is skipped by default
 * during unit test runs due to JaCoCo + Quarkus bytecode conflicts.
 * Disabled due to ExceptionInInitializerError with JaCoCo bytecode instrumentation.
 */
@Disabled("Disabled due to JaCoCo + Quarkus bytecode conflicts causing ExceptionInInitializerError")
@QuarkusTest
class TemplateValidatorQuarkusIT {

    @Inject
    TemplateValidator validator;

    @Test
    void testValidatorIsInjected() {
        assertNotNull(validator, "TemplateValidator should be injected");
    }

    @Test
    void testValidatorWithInvalidOdt() {
        byte[] invalidContent = "not-an-odt-file".getBytes();
        ValidationResult result = validator.validate(invalidContent);

        assertNotNull(result);
        assertFalse(result.isValid());
        assertFalse(result.errors().isEmpty());
        assertEquals("INVALID_ODT_STRUCTURE", result.errors().get(0).code());
    }

    @Test
    void testValidatorWithEmptyContent() {
        byte[] empty = new byte[0];
        ValidationResult result = validator.validate(empty);

        assertNotNull(result);
        assertFalse(result.isValid());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void testValidationResultStructure() {
        byte[] invalidContent = "test".getBytes();
        ValidationResult result = validator.validate(invalidContent);

        assertNotNull(result.errors());
        assertNotNull(result.warnings());
        assertNotNull(result.userFields());
        assertNotNull(result.repetitionGroups());
        assertNotNull(result.conditions());
    }

    @Test
    void testValidatorWithGarbageContent() {
        byte[] garbage = new byte[]{1, 2, 3, 4, 5};
        ValidationResult result = validator.validate(garbage);

        assertNotNull(result);
        assertFalse(result.isValid());
    }

    @Test
    void testValidatorHandlesMultipleCalls() {
        byte[] content1 = "content1".getBytes();
        byte[] content2 = "content2".getBytes();

        ValidationResult result1 = validator.validate(content1);
        ValidationResult result2 = validator.validate(content2);

        assertNotNull(result1);
        assertNotNull(result2);
        assertFalse(result1.isValid());
        assertFalse(result2.isValid());
    }

    @Test
    void testValidatorWithRealOdtIfAvailable() throws Exception {
        Path odtPath = Paths.get("blocpress-core/src/test/resources/sample-04.odt");

        if (Files.exists(odtPath)) {
            byte[] odtContent = Files.readAllBytes(odtPath);
            ValidationResult result = validator.validate(odtContent);

            assertNotNull(result);
            // Real ODT should parse (may have errors or be valid)
            assertNotNull(result.userFields());
            assertNotNull(result.repetitionGroups());
            assertNotNull(result.conditions());
        }
    }

    @Test
    void testValidatorWithAnotherRealOdt() throws Exception {
        Path odtPath = Paths.get("blocpress-core/src/test/resources/sample-05.odt");

        if (Files.exists(odtPath)) {
            byte[] odtContent = Files.readAllBytes(odtPath);
            ValidationResult result = validator.validate(odtContent);

            assertNotNull(result);
            assertNotNull(result.userFields());
        }
    }

    @Test
    void testValidatorErrorHandling() {
        // Test that validator returns proper error structure
        byte[] badContent = "this is definitely not an odt file".getBytes();
        ValidationResult result = validator.validate(badContent);

        assertTrue(result.errors().size() > 0 || !result.isValid());
    }

    @Test
    void testValidatorConsistency() {
        byte[] content = "test-content".getBytes();

        ValidationResult result1 = validator.validate(content);
        ValidationResult result2 = validator.validate(content);

        // Same input should produce same validity status
        assertEquals(result1.isValid(), result2.isValid());
    }
}
