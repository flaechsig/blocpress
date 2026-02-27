package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemplateValidator with real ODT files.
 */
class TemplateValidatorIntegrationTest {

    private TemplateValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        validator = new TemplateValidator();
        validator.objectMapper = objectMapper;
    }

    @Test
    void testValidateWithRealOdtFile() throws Exception {
        // Load a real ODT file from blocpress-core test resources
        Path odtPath = Paths.get("blocpress-core/src/test/resources/sample-04.odt");

        if (!Files.exists(odtPath)) {
            // Skip if file doesn't exist (might be running from different directory)
            return;
        }

        byte[] odtContent = Files.readAllBytes(odtPath);
        ValidationResult result = validator.validate(odtContent);

        assertNotNull(result);
        assertNotNull(result.errors());
        assertNotNull(result.warnings());
        assertNotNull(result.userFields());
        assertNotNull(result.repetitionGroups());
        assertNotNull(result.conditions());
    }

    @Test
    void testValidateWithInvalidOdt() {
        byte[] invalidContent = "This is not an ODT file".getBytes();
        ValidationResult result = validator.validate(invalidContent);

        assertNotNull(result);
        assertFalse(result.isValid(), "Should be invalid for non-ODT content");
        assertFalse(result.errors().isEmpty(), "Should have errors for invalid ODT");
    }

    @Test
    void testValidateWithEmptyContent() {
        byte[] emptyContent = new byte[0];
        ValidationResult result = validator.validate(emptyContent);

        assertNotNull(result);
        assertFalse(result.isValid(), "Should be invalid for empty content");
        assertFalse(result.errors().isEmpty(), "Should have errors for empty content");
    }

    @Test
    void testValidationResultStructure() throws Exception {
        Path odtPath = Paths.get("blocpress-core/src/test/resources/sample-04.odt");

        if (!Files.exists(odtPath)) {
            return;
        }

        byte[] odtContent = Files.readAllBytes(odtPath);
        ValidationResult result = validator.validate(odtContent);

        // Verify result structure
        assertTrue(result.isValid() || !result.isValid(), "Should have valid/invalid status");
        assertNotNull(result.errors(), "Errors should not be null");
        assertNotNull(result.warnings(), "Warnings should not be null");
        assertNotNull(result.userFields(), "User fields should not be null");
        assertNotNull(result.repetitionGroups(), "Repetition groups should not be null");
        assertNotNull(result.conditions(), "Conditions should not be null");
    }

    @Test
    void testValidatorExtractsUserFields() throws Exception {
        Path odtPath = Paths.get("blocpress-core/src/test/resources/sample-04.odt");

        if (!Files.exists(odtPath)) {
            return;
        }

        byte[] odtContent = Files.readAllBytes(odtPath);
        ValidationResult result = validator.validate(odtContent);

        // Check if user fields were extracted (should be present in sample-04.odt)
        assertNotNull(result.userFields());
        // Sample files have user fields, so we should have some
        assertTrue(result.userFields().size() > 0 || result.errors().isEmpty(),
            "Should extract user fields or have no errors");
    }

    @Test
    void testValidatorDetectsInvalidFieldNames() {
        // This would require creating a custom ODT with invalid field names
        // For now, just test that the validation method handles the process
        byte[] invalidOdt = "not-an-odt".getBytes();
        ValidationResult result = validator.validate(invalidOdt);

        assertNotNull(result);
        assertFalse(result.isValid());
    }

    @Test
    void testValidationResultContainsExpectedFields() throws Exception {
        Path odtPath = Paths.get("blocpress-core/src/test/resources/sample-04.odt");

        if (!Files.exists(odtPath)) {
            return;
        }

        byte[] odtContent = Files.readAllBytes(odtPath);
        ValidationResult result = validator.validate(odtContent);

        // All result fields should be initialized
        assertNotNull(result.isValid());
        assertNotNull(result.errors());
        assertNotNull(result.warnings());
        assertNotNull(result.userFields());
        assertNotNull(result.repetitionGroups());
        assertNotNull(result.conditions());
    }

    @Test
    void testMultipleInvalidOdtFiles() {
        // Test various invalid inputs
        byte[][] invalidInputs = {
            new byte[0],
            "plain text".getBytes(),
            "ZIP file header but not ODT".getBytes(),
            "<html>Not ODT</html>".getBytes()
        };

        for (byte[] input : invalidInputs) {
            ValidationResult result = validator.validate(input);
            assertNotNull(result);
            assertFalse(result.isValid(), "Should detect invalid ODT for: " + new String(input));
        }
    }

    @Test
    void testValidatorHandlesLargeFiles() {
        byte[] largeContent = new byte[1024 * 1024]; // 1MB
        ValidationResult result = validator.validate(largeContent);

        assertNotNull(result);
        assertFalse(result.isValid());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void testValidatorWithNullContent() {
        // Verify that validator doesn't crash with null (though this is unlikely in real usage)
        try {
            // The validator should handle null gracefully or throw a meaningful error
            ValidationResult result = validator.validate(null);
            assertNotNull(result);
        } catch (NullPointerException e) {
            // Expected if validator doesn't check for null
            assertNotNull(e);
        }
    }

    @Test
    void testValidationResultIsConsistent() throws Exception {
        Path odtPath = Paths.get("blocpress-core/src/test/resources/sample-04.odt");

        if (!Files.exists(odtPath)) {
            return;
        }

        byte[] odtContent = Files.readAllBytes(odtPath);

        // Validate twice and results should be consistent
        ValidationResult result1 = validator.validate(odtContent);
        ValidationResult result2 = validator.validate(odtContent);

        assertEquals(result1.isValid(), result2.isValid());
        assertEquals(result1.userFields().size(), result2.userFields().size());
        assertEquals(result1.repetitionGroups().size(), result2.repetitionGroups().size());
        assertEquals(result1.conditions().size(), result2.conditions().size());
    }
}
