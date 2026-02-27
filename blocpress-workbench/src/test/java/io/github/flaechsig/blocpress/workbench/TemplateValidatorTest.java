package io.github.flaechsig.blocpress.workbench;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemplateValidator field name validation.
 * (Integration tests with full ODT validation require @QuarkusTest with DB setup)
 */
class TemplateValidatorTest {

    private static final String FIELD_NAME_PATTERN = "^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$";

    @Test
    void testValidFieldNames() {
        assertTrue("customer".matches(FIELD_NAME_PATTERN));
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
        "0firstName",        // starts with digit
        "first-name",        // contains dash
        ".firstName",        // starts with dot
        "firstName.",        // ends with dot
        "first..name",       // consecutive dots
        "first.0name",       // component starts with digit
        "_firstName"         // starts with underscore
    })
    void testInvalidFieldNamePatterns(String fieldName) {
        assertFalse(fieldName.matches(FIELD_NAME_PATTERN),
            "Field name should not match pattern: " + fieldName);
    }
}
