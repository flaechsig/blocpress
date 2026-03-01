package io.github.flaechsig.blocpress.render;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema Validation Tests for render module — ensures production schema matches entity definitions.
 * These tests catch common schema migration issues early, especially critical since
 * render's production schema is separate from workbench's development schema.
 */
@QuarkusTest
class SchemaValidationIT {

    @Inject
    EntityManager em;

    /**
     * Verify that all required columns exist in the production.template table.
     * This catches schema drift / migration failures early.
     */
    @Test
    @Transactional
    void productionTemplateTableHasRequiredColumns() {
        // Query the database schema directly
        var query = em.createNativeQuery(
            "SELECT column_name FROM information_schema.columns " +
            "WHERE table_schema = 'production' AND table_name = 'template'"
        );

        @SuppressWarnings("unchecked")
        var columns = (java.util.List<String>) query.getResultList();

        assertTrue(columns.contains("id"), "Missing column: id");
        assertTrue(columns.contains("name"), "Missing column: name");
        assertTrue(columns.contains("version"), "Missing column: version");
        assertTrue(columns.contains("content"), "Missing column: content");
        assertTrue(columns.contains("valid_from"), "Missing column: valid_from");
    }

    /**
     * Simple smoke test: Can we create and query a ProductionTemplate?
     * This would fail if the schema is broken.
     */
    @Test
    @Transactional
    void canPersistAndQueryProductionTemplate() {
        ProductionTemplate t = new ProductionTemplate();
        t.name = "SchemaTest";
        t.version = 1;
        t.content = "test".getBytes();
        t.persist();

        // This would fail if schema columns are missing
        ProductionTemplate found = ProductionTemplate.findLatestActiveByName("SchemaTest");
        assertNotNull(found, "ProductionTemplate not found after persist");
        assertEquals("SchemaTest", found.name);
        assertEquals(1, found.version);
    }

    /**
     * Verify that the production schema exists and is accessible.
     * This catches connection/permissions issues.
     */
    @Test
    @Transactional
    void productionSchemaIsAccessible() {
        var query = em.createNativeQuery(
            "SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'production'"
        );

        @SuppressWarnings("unchecked")
        var results = (java.util.List<?>) query.getResultList();

        assertTrue(results.size() > 0,
            "Schema 'production' does not exist or is not accessible. " +
            "Check database connection and schema creation strategy."
        );
    }
}
