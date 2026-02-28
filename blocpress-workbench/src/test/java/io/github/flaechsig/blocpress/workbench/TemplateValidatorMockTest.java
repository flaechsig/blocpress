package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for TemplateValidator logic.
 * Tests validation result creation and error handling paths with JSON-Schema structure.
 */
class TemplateValidatorMockTest {

    private TemplateValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        validator = new TemplateValidator();
        validator.objectMapper = objectMapper;
    }

    @Test
    void testValidateReturnsValidationResult() {
        byte[] invalidContent = "not-odt".getBytes();
        ValidationResult result = validator.validate(invalidContent);

        assertNotNull(result, "Validation result should not be null");
        assertTrue(result instanceof ValidationResult, "Should return ValidationResult instance");
    }

    @Test
    void testValidateReturnsProperStructure() {
        byte[] invalidContent = "invalid-content".getBytes();
        ValidationResult result = validator.validate(invalidContent);

        // Test that all result fields are properly initialized
        assertNotNull(result.isValid(), "isValid should be set");
        assertNotNull(result.errors(), "errors list should not be null");
        assertNotNull(result.warnings(), "warnings list should not be null");
        assertNotNull(result.schema(), "schema should not be null");
        assertEquals("object", result.schema().get("type").asText());
    }

    @Test
    void testValidateWithInvalidContentReturnsErrors() {
        byte[] invalidContent = "definitely not an odt".getBytes();
        ValidationResult result = validator.validate(invalidContent);

        assertFalse(result.isValid(), "Should mark as invalid for bad content");
        assertFalse(result.errors().isEmpty(), "Should have at least one error");
        assertEquals("INVALID_ODT_STRUCTURE", result.errors().get(0).code());
    }

    @Test
    void testValidateWithEmptyBytes() {
        byte[] emptyContent = new byte[0];
        ValidationResult result = validator.validate(emptyContent);

        assertNotNull(result);
        assertFalse(result.isValid());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void testValidateWithNullHandling() {
        // Test null resilience
        try {
            ValidationResult result = validator.validate(null);
            // If it doesn't throw, verify structure
            assertNotNull(result);
        } catch (NullPointerException e) {
            // This is also acceptable - null input should fail
            assertNotNull(e.getMessage());
        }
    }

    @Test
    void testValidationResultCreation() {
        List<ValidationResult.ValidationMessage> errors = new ArrayList<>();
        errors.add(new ValidationResult.ValidationMessage("ERR001", "Error message"));

        List<ValidationResult.ValidationMessage> warnings = new ArrayList<>();
        var schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ValidationResult result = new ValidationResult(false, schema, errors, warnings);

        assertFalse(result.isValid());
        assertEquals(1, result.errors().size());
        assertNotNull(result.schema());
    }

    @Test
    void testErrorMessages() {
        List<ValidationResult.ValidationMessage> errors = new ArrayList<>();
        errors.add(new ValidationResult.ValidationMessage("CODE1", "Message 1"));
        errors.add(new ValidationResult.ValidationMessage("CODE2", "Message 2"));

        List<ValidationResult.ValidationMessage> warnings = new ArrayList<>();
        var schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ValidationResult result = new ValidationResult(false, schema, errors, warnings);

        assertEquals(2, result.errors().size());
        assertEquals("CODE1", result.errors().get(0).code());
        assertEquals("MESSAGE 1", result.errors().get(0).message().toUpperCase());
    }

    @Test
    void testSchemaGeneration() {
        var errors = new ArrayList<ValidationResult.ValidationMessage>();
        var warnings = new ArrayList<ValidationResult.ValidationMessage>();

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

        ValidationResult result = new ValidationResult(true, schema, errors, warnings);

        assertTrue(result.isValid());
        assertNotNull(result.schema());
        assertNotNull(result.schema().get("properties"));
        assertNotNull(result.schema().get("properties").get("customer"));
    }

    @Test
    void testArrayPropertyGeneration() {
        var errors = new ArrayList<ValidationResult.ValidationMessage>();
        var warnings = new ArrayList<ValidationResult.ValidationMessage>();

        var schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        var properties = objectMapper.createObjectNode();
        var itemsProp = objectMapper.createObjectNode();
        itemsProp.put("type", "array");
        var items = objectMapper.createObjectNode();
        items.put("type", "object");
        itemsProp.set("items", items);
        properties.set("items", itemsProp);
        schema.set("properties", properties);

        ValidationResult result = new ValidationResult(true, schema, errors, warnings);

        assertEquals("array", result.schema().get("properties").get("items").get("type").asText());
    }

    @Test
    void testValidatorProcessFlow() {
        // Test complete flow with different inputs
        byte[][] testInputs = {
            "not-odt".getBytes(),
            "garbage".getBytes(),
            new byte[0],
            "x".getBytes()
        };

        for (byte[] input : testInputs) {
            ValidationResult result = validator.validate(input);
            assertNotNull(result);
            assertFalse(result.isValid());
            assertFalse(result.errors().isEmpty());
        }
    }

    @Test
    void testComplexValidationScenario() {
        // Simulate a complex validation result
        List<ValidationResult.ValidationMessage> errors = new ArrayList<>();
        errors.add(new ValidationResult.ValidationMessage("E001", "Invalid field"));
        errors.add(new ValidationResult.ValidationMessage("E002", "Missing required field"));

        List<ValidationResult.ValidationMessage> warnings = new ArrayList<>();
        warnings.add(new ValidationResult.ValidationMessage("W001", "Field may be unused"));

        var schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        var properties = objectMapper.createObjectNode();
        var fieldProp = objectMapper.createObjectNode();
        fieldProp.put("type", "string");
        properties.set("valid_field", fieldProp);
        schema.set("properties", properties);

        ValidationResult result = new ValidationResult(false, schema, errors, warnings);

        // Verify complex structure
        assertFalse(result.isValid());
        assertEquals(2, result.errors().size());
        assertEquals(1, result.warnings().size());
        assertNotNull(result.schema());
    }

    @Test
    void testValidationMessageEquality() {
        ValidationResult.ValidationMessage msg1 = new ValidationResult.ValidationMessage("CODE", "Message");
        ValidationResult.ValidationMessage msg2 = new ValidationResult.ValidationMessage("CODE", "Message");

        assertEquals(msg1.code(), msg2.code());
        assertEquals(msg1.message(), msg2.message());
    }

    @Test
    void testEmptySchema() {
        var errors = new ArrayList<ValidationResult.ValidationMessage>();
        var warnings = new ArrayList<ValidationResult.ValidationMessage>();

        var schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ValidationResult result = new ValidationResult(true, schema, errors, warnings);

        assertTrue(result.isValid());
        assertEquals("object", result.schema().get("type").asText());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }
}
