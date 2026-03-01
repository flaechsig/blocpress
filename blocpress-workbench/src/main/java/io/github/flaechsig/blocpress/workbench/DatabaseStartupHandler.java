package io.github.flaechsig.blocpress.workbench;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.logging.Logger;

/**
 * Development-only startup handler that resets the database schema.
 * Enables "clean sheet" development where schema is always recreated from Liquibase changesets.
 *
 * Only active in development profile (%dev).
 */
@ApplicationScoped
public class DatabaseStartupHandler {

    private static final Logger LOGGER = Logger.getLogger(DatabaseStartupHandler.class.getName());

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "quarkus.profile")
    String profile;

    void onStart(@Observes StartupEvent event) {
        // Only run in development profile
        if (!"dev".equals(profile)) {
            LOGGER.fine("Not in dev profile, skipping database reset");
            return;
        }

        try {
            LOGGER.info("Development mode: Resetting database schema...");

            // Drop all tables and functions
            String dropAllSQL = """
                    DO $$
                    DECLARE r RECORD;
                    BEGIN
                        -- Drop all foreign keys
                        FOR r IN (SELECT constraint_name, table_name
                                  FROM information_schema.table_constraints
                                  WHERE constraint_type = 'FOREIGN KEY')
                        LOOP
                            EXECUTE 'ALTER TABLE ' || r.table_name || ' DROP CONSTRAINT ' || r.constraint_name;
                        END LOOP;

                        -- Drop all tables
                        FOR r IN (SELECT tablename FROM pg_tables
                                  WHERE schemaname = 'public')
                        LOOP
                            EXECUTE 'DROP TABLE IF EXISTS ' || r.tablename || ' CASCADE';
                        END LOOP;

                        -- Drop liquibase tables
                        DROP TABLE IF EXISTS databasechangelog CASCADE;
                        DROP TABLE IF EXISTS databasechangeloglock CASCADE;

                        -- Drop functions/procedures
                        FOR r IN (SELECT routinenamespace, routinename
                                  FROM information_schema.routines
                                  WHERE routinenamespace NOT IN (
                                      SELECT oid FROM pg_namespace WHERE nspname = 'pg_catalog'
                                  ))
                        LOOP
                            EXECUTE 'DROP FUNCTION IF EXISTS ' || r.routinenamespace || '.' || r.routinename || ' CASCADE';
                        END LOOP;
                    END $$;
                    """;

            // Execute drop script
            entityManager.createNativeQuery(dropAllSQL).executeUpdate();
            LOGGER.info("Database schema reset complete. Liquibase will recreate tables on next access.");

        } catch (Exception e) {
            LOGGER.warning("Error resetting database schema: " + e.getMessage());
            // Don't fail startup if reset fails - Liquibase will still initialize
        }
    }
}
