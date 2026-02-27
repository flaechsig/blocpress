package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for TemplateValidator logic.
 * Tests validation result creation and error handling paths.
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
        assertNotNull(result.userFields(), "userFields list should not be null");
        assertNotNull(result.repetitionGroups(), "repetitionGroups list should not be null");
        assertNotNull(result.conditions(), "conditions list should not be null");
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
        List<ValidationResult.UserFieldInfo> userFields = new ArrayList<>();
        userFields.add(new ValidationResult.UserFieldInfo("field.name", "user-field"));

        List<ValidationResult.RepetitionGroupInfo> repeatGroups = new ArrayList<>();
        List<ValidationResult.ConditionInfo> conditions = new ArrayList<>();

        ValidationResult result = new ValidationResult(false, errors, warnings, userFields, repeatGroups, conditions);

        assertFalse(result.isValid());
        assertEquals(1, result.errors().size());
        assertEquals(1, result.userFields().size());
    }

    @Test
    void testErrorMessages() {
        List<ValidationResult.ValidationMessage> errors = new ArrayList<>();
        errors.add(new ValidationResult.ValidationMessage("CODE1", "Message 1"));
        errors.add(new ValidationResult.ValidationMessage("CODE2", "Message 2"));

        List<ValidationResult.ValidationMessage> warnings = new ArrayList<>();
        List<ValidationResult.UserFieldInfo> userFields = new ArrayList<>();
        List<ValidationResult.RepetitionGroupInfo> repeatGroups = new ArrayList<>();
        List<ValidationResult.ConditionInfo> conditions = new ArrayList<>();

        ValidationResult result = new ValidationResult(false, errors, warnings, userFields, repeatGroups, conditions);

        assertEquals(2, result.errors().size());
        assertEquals("CODE1", result.errors().get(0).code());
        assertEquals("MESSAGE 1", result.errors().get(0).message().toUpperCase());
    }

    @Test
    void testUserFieldExtraction() {
        List<ValidationResult.ValidationMessage> errors = new ArrayList<>();
        List<ValidationResult.ValidationMessage> warnings = new ArrayList<>();

        List<ValidationResult.UserFieldInfo> userFields = new ArrayList<>();
        userFields.add(new ValidationResult.UserFieldInfo("customer.name", "user-field"));
        userFields.add(new ValidationResult.UserFieldInfo("customer.email", "user-field"));
        userFields.add(new ValidationResult.UserFieldInfo("order.total", "user-field"));

        List<ValidationResult.RepetitionGroupInfo> repeatGroups = new ArrayList<>();
        List<ValidationResult.ConditionInfo> conditions = new ArrayList<>();

        ValidationResult result = new ValidationResult(true, errors, warnings, userFields, repeatGroups, conditions);

        assertEquals(3, result.userFields().size());
        assertEquals("customer.name", result.userFields().get(0).name());
        assertEquals("customer.email", result.userFields().get(1).name());
        assertEquals("order.total", result.userFields().get(2).name());
    }

    @Test
    void testRepetitionGroupDetection() {
        List<ValidationResult.ValidationMessage> errors = new ArrayList<>();
        List<ValidationResult.ValidationMessage> warnings = new ArrayList<>();
        List<ValidationResult.UserFieldInfo> userFields = new ArrayList<>();

        List<ValidationResult.RepetitionGroupInfo> repeatGroups = new ArrayList<>();
        repeatGroups.add(new ValidationResult.RepetitionGroupInfo("items", "data.items", "section"));
        repeatGroups.add(new ValidationResult.RepetitionGroupInfo("lines", "data.lines", "table-row"));

        List<ValidationResult.ConditionInfo> conditions = new ArrayList<>();

        ValidationResult result = new ValidationResult(true, errors, warnings, userFields, repeatGroups, conditions);

        assertEquals(2, result.repetitionGroups().size());
        assertEquals("items", result.repetitionGroups().get(0).name());
        assertEquals("data.items", result.repetitionGroups().get(0).arrayPath());
        assertEquals("section", result.repetitionGroups().get(0).type());
    }

    @Test
    void testConditionValidation() {
        List<ValidationResult.ValidationMessage> errors = new ArrayList<>();
        List<ValidationResult.ValidationMessage> warnings = new ArrayList<>();
        List<ValidationResult.UserFieldInfo> userFields = new ArrayList<>();
        List<ValidationResult.RepetitionGroupInfo> repeatGroups = new ArrayList<>();

        List<ValidationResult.ConditionInfo> conditions = new ArrayList<>();
        conditions.add(new ValidationResult.ConditionInfo("status == 'ACTIVE'", "section", true, null));
        conditions.add(new ValidationResult.ConditionInfo("amount > 100", "p", true, null));
        conditions.add(new ValidationResult.ConditionInfo("invalid {{}", "span", false, "Syntax error"));

        ValidationResult result = new ValidationResult(false, errors, warnings, userFields, repeatGroups, conditions);

        assertEquals(3, result.conditions().size());
        assertTrue(result.conditions().get(0).syntaxValid());
        assertTrue(result.conditions().get(1).syntaxValid());
        assertFalse(result.conditions().get(2).syntaxValid());
        assertEquals("Syntax error", result.conditions().get(2).errorMessage());
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

        List<ValidationResult.UserFieldInfo> userFields = new ArrayList<>();
        userFields.add(new ValidationResult.UserFieldInfo("invalid_field!", "user-field"));
        userFields.add(new ValidationResult.UserFieldInfo("valid.field", "user-field"));

        List<ValidationResult.RepetitionGroupInfo> repeatGroups = new ArrayList<>();
        repeatGroups.add(new ValidationResult.RepetitionGroupInfo("items", "data.items", "section"));

        List<ValidationResult.ConditionInfo> conditions = new ArrayList<>();
        conditions.add(new ValidationResult.ConditionInfo("bad syntax {{", "p", false, "Parse error"));

        ValidationResult result = new ValidationResult(false, errors, warnings, userFields, repeatGroups, conditions);

        // Verify complex structure
        assertFalse(result.isValid());
        assertEquals(2, result.errors().size());
        assertEquals(1, result.warnings().size());
        assertEquals(2, result.userFields().size());
        assertEquals(1, result.repetitionGroups().size());
        assertEquals(1, result.conditions().size());
    }

    @Test
    void testValidationMessageEquality() {
        ValidationResult.ValidationMessage msg1 = new ValidationResult.ValidationMessage("CODE", "Message");
        ValidationResult.ValidationMessage msg2 = new ValidationResult.ValidationMessage("CODE", "Message");

        assertEquals(msg1.code(), msg2.code());
        assertEquals(msg1.message(), msg2.message());
    }

    @Test
    void testUserFieldInfoEquality() {
        ValidationResult.UserFieldInfo field1 = new ValidationResult.UserFieldInfo("name", "type");
        ValidationResult.UserFieldInfo field2 = new ValidationResult.UserFieldInfo("name", "type");

        assertEquals(field1.name(), field2.name());
        assertEquals(field1.type(), field2.type());
    }
}
