package io.github.flaechsig.blocpress.render;

import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * REQ-0028: render bringt die Datenbank production beim Start mit Liquibase auf den Stand der
 * Entitäten, auf leerer wie auf bestehender Datenbank. Geprüft gegen PostgreSQL 18 mit dem
 * Changelog aus {@code db/changeLog.xml} und der Schemaprüfung von Hibernate, die dasselbe
 * prüft wie {@code schema-management.strategy=validate} beim Start.
 */
class LiquibaseMigrationTest {

    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:18")
            .withEnv("POSTGRES_USER", "blocpress")
            .withEnv("POSTGRES_PASSWORD", "blocpress")
            .withEnv("POSTGRES_DB", "production")
            .withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

    @BeforeAll
    static void start() {
        POSTGRES.start();
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @BeforeEach
    void emptyDatabase() throws Exception {
        try (Connection c = connect()) {
            c.createStatement().execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        }
    }

    @Test
    @DisplayName("REQ-0028: emptyDatabaseIsMigratedToEntitySchema")
    void emptyDatabaseIsMigratedToEntitySchema() throws Exception {
        migrate();

        assertEquals("EXECUTED", changeSetState("001-initial-schema"));
        assertDoesNotThrow(() -> validateWithHibernate());
    }

    @Test
    @DisplayName("REQ-0028: existingDatabaseIsAdoptedWithoutDataLoss")
    void existingDatabaseIsAdoptedWithoutDataLoss() throws Exception {
        // so entstand die Datenbank bis 2.7.0: Hibernate legt die Tabellen an, ohne Liquibase
        try (SessionFactory sf = sessionFactory()) {
            sf.getSchemaManager().create(true);
        }
        try (Connection c = connect()) {
            c.createStatement().execute("INSERT INTO template (id, name, valid_from, version, content) "
                    + "VALUES (gen_random_uuid(), 'rechnung', now(), 1, '\\x00')");
        }

        migrate();

        assertEquals("MARK_RAN", changeSetState("001-initial-schema"));
        assertEquals(1, count("SELECT COUNT(*) FROM template WHERE name = 'rechnung'"));
        assertDoesNotThrow(() -> validateWithHibernate());
    }

    @Test
    void validationFailsWithoutMigration() {
        // Gegenprobe: die Pruefung erkennt ein fehlendes Schema
        assertThrows(Exception.class, LiquibaseMigrationTest::validateWithHibernate);
    }

    private static void migrate() throws Exception {
        try (Connection c = connect()) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));
            try (var liquibase = new Liquibase("db/changeLog.xml", new ClassLoaderResourceAccessor(), database)) {
                liquibase.update();
            }
        }
    }

    private static void validateWithHibernate() throws Exception {
        try (SessionFactory sf = sessionFactory()) {
            sf.getSchemaManager().validate();
        }
    }

    private static SessionFactory sessionFactory() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", url())
                .applySetting("hibernate.connection.username", "blocpress")
                .applySetting("hibernate.connection.password", "blocpress")
                .build();
        return new MetadataSources(registry)
                .addAnnotatedClass(ProductionTemplate.class)
                .addAnnotatedClass(RenderJob.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    private static String changeSetState(String id) throws Exception {
        try (Connection c = connect();
             ResultSet rs = c.createStatement().executeQuery(
                     "SELECT exectype FROM databasechangelog WHERE id = '" + id + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static int count(String sql) throws Exception {
        try (Connection c = connect(); ResultSet rs = c.createStatement().executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(url(), "blocpress", "blocpress");
    }

    private static String url() {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/production";
    }
}
