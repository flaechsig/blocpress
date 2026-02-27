package io.github.flaechsig.blocpress.workbench;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemplateValidator field name validation and data structures.
 * (Integration tests with full ODT validation require @QuarkusTest with DB setup)
 */
class TemplateValidatorTest {

    private static final String FIELD_NAME_PATTERN = "^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$";

    // ===== Field Name Validation Tests =====

    @Test
    void testValidFieldNames() {
        assertTrue("customer".matches(FIELD_NAME_PATTERN));
        assertTrue("customer_name".matches(FIELD_NAME_PATTERN));
        assertTrue("customer123".matches(FIELD_NAME_PATTERN));
    }

    @Test
    void testValidDottedFieldNames() {
        assertTrue("customer.name".matches(FIELD_NAME_PATTERN));
        assertTrue("customer.address.street".matches(FIELD_NAME_PATTERN));
        assertTrue("customer.contact.email".matches(FIELD_NAME_PATTERN));
        assertTrue("data.items.name".matches(FIELD_NAME_PATTERN));
    }

    @Test
    void testInvalidFieldNames() {
        assertFalse("123customer".matches(FIELD_NAME_PATTERN), "Should not start with number");
        assertFalse("customer-name".matches(FIELD_NAME_PATTERN), "Should not contain dash");
        assertFalse(".customer".matches(FIELD_NAME_PATTERN), "Should not start with dot");
        assertFalse("customer.".matches(FIELD_NAME_PATTERN), "Should not end with dot");
        assertFalse("customer..name".matches(FIELD_NAME_PATTERN), "Should not have consecutive dots");
        assertFalse("customer.123name".matches(FIELD_NAME_PATTERN), "Field after dot should not start with number");
        assertFalse("_customer".matches(FIELD_NAME_PATTERN), "Should not start with underscore");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "firstName",
        "lastName",
        "street_address",
        "customer.firstName",
        "address.street.number",
        "items.name"
    })
    void testValidFieldNamePatterns(String fieldName) {
        assertTrue(fieldName.matches(FIELD_NAME_PATTERN),
            "Field name should match pattern: " + fieldName);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "0firstName",
        "first-name",
        ".firstName",
        "firstName.",
        "first..name",
        "first.0name",
        "_firstName"
    })
    void testInvalidFieldNamePatterns(String fieldName) {
        assertFalse(fieldName.matches(FIELD_NAME_PATTERN),
            "Field name should not match pattern: " + fieldName);
    }

    // ===== TemplateStatus Tests =====

    @Test
    void testTemplateStatusEnumValues() {
        assertNotNull(TemplateStatus.DRAFT);
        assertNotNull(TemplateStatus.SUBMITTED);
        assertNotNull(TemplateStatus.APPROVED);
        assertNotNull(TemplateStatus.REJECTED);
        assertEquals(4, TemplateStatus.values().length);
    }

    @Test
    void testTemplateStatusValueOf() {
        assertEquals(TemplateStatus.DRAFT, TemplateStatus.valueOf("DRAFT"));
        assertEquals(TemplateStatus.SUBMITTED, TemplateStatus.valueOf("SUBMITTED"));
        assertEquals(TemplateStatus.APPROVED, TemplateStatus.valueOf("APPROVED"));
        assertEquals(TemplateStatus.REJECTED, TemplateStatus.valueOf("REJECTED"));
    }

    // ===== ValidationResult Tests =====

    @Test
    void testValidationResultEmptyRecord() {
        var errors = java.util.List.<ValidationResult.ValidationMessage>of();
        var warnings = java.util.List.<ValidationResult.ValidationMessage>of();
        var userFields = java.util.List.<ValidationResult.UserFieldInfo>of();
        var repeatGroups = java.util.List.<ValidationResult.RepetitionGroupInfo>of();
        var conditions = java.util.List.<ValidationResult.ConditionInfo>of();

        ValidationResult result = new ValidationResult(true, errors, warnings, userFields, repeatGroups, conditions);

        assertTrue(result.isValid());
        assertTrue(result.errors().isEmpty());
        assertTrue(result.warnings().isEmpty());
        assertTrue(result.userFields().isEmpty());
        assertTrue(result.repetitionGroups().isEmpty());
        assertTrue(result.conditions().isEmpty());
    }

    @Test
    void testValidationResultInvalid() {
        var errors = java.util.List.of(
            new ValidationResult.ValidationMessage("TEST_ERROR", "Test error message")
        );
        var warnings = java.util.List.<ValidationResult.ValidationMessage>of();
        var userFields = java.util.List.<ValidationResult.UserFieldInfo>of();
        var repeatGroups = java.util.List.<ValidationResult.RepetitionGroupInfo>of();
        var conditions = java.util.List.<ValidationResult.ConditionInfo>of();

        ValidationResult result = new ValidationResult(false, errors, warnings, userFields, repeatGroups, conditions);

        assertFalse(result.isValid());
        assertEquals(1, result.errors().size());
        assertEquals("TEST_ERROR", result.errors().get(0).code());
    }

    // ===== ValidationMessage Tests =====

    @Test
    void testValidationMessageRecord() {
        ValidationResult.ValidationMessage msg =
            new ValidationResult.ValidationMessage("INVALID_FIELD", "Field is invalid");

        assertEquals("INVALID_FIELD", msg.code());
        assertEquals("Field is invalid", msg.message());
    }

    // ===== UserFieldInfo Tests =====

    @Test
    void testUserFieldInfoRecord() {
        ValidationResult.UserFieldInfo field =
            new ValidationResult.UserFieldInfo("customer.firstName", "user-field");

        assertEquals("customer.firstName", field.name());
        assertEquals("user-field", field.type());
    }

    @Test
    void testUserFieldInfoMultiple() {
        var fields = java.util.List.of(
            new ValidationResult.UserFieldInfo("customer.firstName", "user-field"),
            new ValidationResult.UserFieldInfo("customer.lastName", "user-field"),
            new ValidationResult.UserFieldInfo("customer.email", "user-field")
        );

        assertEquals(3, fields.size());
        assertEquals("customer.firstName", fields.get(0).name());
        assertEquals("customer.lastName", fields.get(1).name());
        assertEquals("customer.email", fields.get(2).name());
    }

    // ===== RepetitionGroupInfo Tests =====

    @Test
    void testRepetitionGroupInfoRecord() {
        ValidationResult.RepetitionGroupInfo group =
            new ValidationResult.RepetitionGroupInfo("items", "data.items", "section");

        assertEquals("items", group.name());
        assertEquals("data.items", group.arrayPath());
        assertEquals("section", group.type());
    }

    @Test
    void testRepetitionGroupInfoMultiple() {
        var groups = java.util.List.of(
            new ValidationResult.RepetitionGroupInfo("items", "data.items", "section"),
            new ValidationResult.RepetitionGroupInfo("lines", "data.lines", "table-row")
        );

        assertEquals(2, groups.size());
        assertEquals("items", groups.get(0).name());
        assertEquals("data.items", groups.get(0).arrayPath());
        assertEquals("lines", groups.get(1).name());
    }

    // ===== ConditionInfo Tests =====

    @Test
    void testConditionInfoRecordValid() {
        ValidationResult.ConditionInfo condition =
            new ValidationResult.ConditionInfo("customer.status == 'ACTIVE'", "conditional-text", true, null);

        assertEquals("customer.status == 'ACTIVE'", condition.expression());
        assertEquals("conditional-text", condition.elementType());
        assertTrue(condition.syntaxValid());
        assertNull(condition.errorMessage());
    }

    @Test
    void testConditionInfoRecordInvalid() {
        ValidationResult.ConditionInfo condition =
            new ValidationResult.ConditionInfo("invalid {{syntax", "section", false, "Unexpected token");

        assertEquals("invalid {{syntax", condition.expression());
        assertEquals("section", condition.elementType());
        assertFalse(condition.syntaxValid());
        assertEquals("Unexpected token", condition.errorMessage());
    }

    @Test
    void testConditionInfoMultiple() {
        var conditions = java.util.List.of(
            new ValidationResult.ConditionInfo("customer.status == 'ACTIVE'", "section", true, null),
            new ValidationResult.ConditionInfo("customer.age > 18", "span", true, null),
            new ValidationResult.ConditionInfo("invalid {}", "p", false, "Syntax error")
        );

        assertEquals(3, conditions.size());
        assertTrue(conditions.get(0).syntaxValid());
        assertTrue(conditions.get(1).syntaxValid());
        assertFalse(conditions.get(2).syntaxValid());
    }
}
