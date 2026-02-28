package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationResult and JSON-Schema structure.
 *
 * Tests verify that the new ValidationResult uses JSON-Schema
 * instead of the old userFields/repetitionGroups/conditions lists.
 */
class TemplateValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testValidationMessageRecord() {
        ValidationResult.ValidationMessage msg =
            new ValidationResult.ValidationMessage("INVALID_FIELD", "Field name is invalid");

        assertEquals("INVALID_FIELD", msg.code());
        assertEquals("Field name is invalid", msg.message());
    }

    @Test
    void testValidationResultWithJsonSchema() {
        var errors = java.util.List.<ValidationResult.ValidationMessage>of();
        var warnings = java.util.List.<ValidationResult.ValidationMessage>of();

        // Create a simple schema
        var schema = mapper.createObjectNode();
        schema.put("type", "object");

        var properties = mapper.createObjectNode();
        var customerProp = mapper.createObjectNode();
        customerProp.put("type", "object");
        properties.set("customer", customerProp);
        schema.set("properties", properties);

        ValidationResult result = new ValidationResult(true, schema, errors, warnings);

        assertTrue(result.isValid());
        assertNotNull(result.schema());
        assertEquals("object", result.schema().get("type").asText());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void testValidationResultWithErrors() {
        var errors = java.util.List.of(
            new ValidationResult.ValidationMessage("ODT_ERROR", "Cannot load ODT")
        );
        var warnings = java.util.List.<ValidationResult.ValidationMessage>of();

        var emptySchema = mapper.createObjectNode();
        emptySchema.put("type", "object");

        ValidationResult result = new ValidationResult(false, emptySchema, errors, warnings);

        assertFalse(result.isValid());
        assertEquals(1, result.errors().size());
        assertEquals("ODT_ERROR", result.errors().get(0).code());
        assertEquals("Cannot load ODT", result.errors().get(0).message());
    }

    @Test
    void testValidationResultWithMultipleWarnings() {
        var errors = java.util.List.<ValidationResult.ValidationMessage>of();
        var warnings = java.util.List.of(
            new ValidationResult.ValidationMessage("INVALID_FIELD_NAME", "Field 'firstName' is not standard"),
            new ValidationResult.ValidationMessage("INVALID_FIELD_NAME", "Field 'items' should be 'itemList'")
        );

        var schema = mapper.createObjectNode();
        schema.put("type", "object");

        ValidationResult result = new ValidationResult(true, schema, errors, warnings);

        assertTrue(result.isValid());
        assertEquals(2, result.warnings().size());
        assertEquals("INVALID_FIELD_NAME", result.warnings().get(0).code());
        assertEquals("INVALID_FIELD_NAME", result.warnings().get(1).code());
    }
}
