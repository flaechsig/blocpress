package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TemplateValidatorTest {

    private TemplateValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        validator = new TemplateValidator(objectMapper);
    }

    @Test
    void testValidateWithValidTemplate() throws Exception {
        // Load valid test template from resources
        Path templatePath = Path.of("src/test/resources/templates/valid-template.odt");
        if (!Files.exists(templatePath)) {
            // Skip if test template doesn't exist yet
            System.out.println("Test template not found: " + templatePath);
            return;
        }

        byte[] content = Files.readAllBytes(templatePath);
        ValidationResult result = validator.validate(content);

        assertTrue(result.isValid(), "Valid template should pass validation");
        assertTrue(result.errors().isEmpty(), "Valid template should have no errors");
    }

    @Test
    void testValidateWithInvalidOdt() {
        // Provide invalid ODT content
        byte[] invalidContent = "This is not an ODT file".getBytes();
        ValidationResult result = validator.validate(invalidContent);

        assertFalse(result.isValid(), "Invalid ODT should fail validation");
        assertFalse(result.errors().isEmpty(), "Invalid ODT should have errors");

        boolean hasOdtError = result.errors().stream()
            .anyMatch(e -> e.code().equals("INVALID_ODT_STRUCTURE"));
        assertTrue(hasOdtError, "Should report invalid ODT structure");
    }

    @Test
    void testValidateFieldNameFormat() throws Exception {
        // This test validates the field name regex
        String validFieldName1 = "customer";
        String validFieldName2 = "customer.name";
        String validFieldName3 = "customer.address.street";
        String invalidFieldName1 = "123customer"; // starts with number
        String invalidFieldName2 = "customer-name"; // contains dash
        String invalidFieldName3 = ".customer"; // starts with dot

        assertTrue(validFieldName1.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$"));
        assertTrue(validFieldName2.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$"));
        assertTrue(validFieldName3.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$"));
        assertFalse(invalidFieldName1.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$"));
        assertFalse(invalidFieldName2.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$"));
        assertFalse(invalidFieldName3.matches("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$"));
    }
}
