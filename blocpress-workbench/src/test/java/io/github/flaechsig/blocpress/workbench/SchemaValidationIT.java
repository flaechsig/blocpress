package io.github.flaechsig.blocpress.workbench;

import io.github.flaechsig.blocpress.workbench.entity.Template;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema Validation Tests — ensures database schema matches entity definitions.
 * These tests catch common schema migration issues early.
 *
 * Related to: https://github.com/flaechsig/blocpress/issues/XXX
 * "ERROR: column t1_0.created_at does not exist" type errors
 */
@QuarkusTest
class SchemaValidationIT {

    @Inject
    EntityManager em;

    /**
     * Verify that all required columns exist in the template table.
     * This catches schema drift / migration failures early.
     */
    @Test
    @Transactional
    void templateTableHasRequiredColumns() {
        // Query the database schema directly
        var query = em.createNativeQuery(
            "SELECT column_name FROM information_schema.columns " +
            "WHERE table_schema = 'workbench' AND table_name = 'template'"
        );

        @SuppressWarnings("unchecked")
        var columns = (java.util.List<String>) query.getResultList();

        assertTrue(columns.contains("id"), "Missing column: id");
        assertTrue(columns.contains("created_at"), "Missing column: created_at");
        assertTrue(columns.contains("name"), "Missing column: name");
        assertTrue(columns.contains("version"), "Missing column: version");
        assertTrue(columns.contains("status"), "Missing column: status");
        assertTrue(columns.contains("valid_from"), "Missing column: valid_from");
        assertTrue(columns.contains("content"), "Missing column: content");
        assertTrue(columns.contains("validation_result"), "Missing column: validation_result");
    }

    /**
     * Verify that all required columns exist in the test_data_set table.
     */
    @Test
    @Transactional
    void testDataSetTableHasRequiredColumns() {
        var query = em.createNativeQuery(
            "SELECT column_name FROM information_schema.columns " +
            "WHERE table_schema = 'workbench' AND table_name = 'test_data_set'"
        );

        @SuppressWarnings("unchecked")
        var columns = (java.util.List<String>) query.getResultList();

        assertTrue(columns.contains("id"), "Missing column: id");
        assertTrue(columns.contains("template_id"), "Missing column: template_id");
        assertTrue(columns.contains("name"), "Missing column: name");
        assertTrue(columns.contains("test_data"), "Missing column: test_data");
        assertTrue(columns.contains("expected_pdf"), "Missing column: expected_pdf");
        assertTrue(columns.contains("pdf_hash"), "Missing column: pdf_hash");
        assertTrue(columns.contains("created_at"), "Missing column: created_at");
        assertTrue(columns.contains("updated_at"), "Missing column: updated_at");
    }

    /**
     * Verify that unique constraints are properly defined.
     */
    @Test
    @Transactional
    void templateHasCompositeUniqueConstraint() {
        // Check for composite unique constraint on (name, version)
        var query = em.createNativeQuery(
            "SELECT constraint_name FROM information_schema.table_constraints " +
            "WHERE table_schema = 'workbench' AND table_name = 'template' " +
            "AND constraint_type = 'UNIQUE'"
        );

        @SuppressWarnings("unchecked")
        var constraints = (java.util.List<String>) query.getResultList();

        assertTrue(constraints.size() > 0,
            "No unique constraint found on template table. " +
            "Expected composite key (name, version) to be enforced."
        );
    }

    /**
     * Simple smoke test: Can we create and query a Template?
     * This would fail if the schema is broken.
     */
    @Test
    @Transactional
    void canPersistAndQueryTemplate() {
        Template t = new Template();
        t.name = "SchemaTest";
        t.version = 1;
        t.content = "test".getBytes();
        t.status = io.github.flaechsig.blocpress.workbench.entity.TemplateStatus.DRAFT;
        t.persist();

        // This would fail if created_at column is missing
        Template found = Template.find("name", "SchemaTest").firstResult();
        assertNotNull(found, "Template not found after persist");
        assertEquals("SchemaTest", found.name);
        assertNotNull(found.createdAt, "createdAt should be auto-set");
    }
}
