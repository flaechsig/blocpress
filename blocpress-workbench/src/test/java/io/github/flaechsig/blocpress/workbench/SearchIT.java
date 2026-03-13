package io.github.flaechsig.blocpress.workbench;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for UC-19 (Content Search via Elasticsearch).
 *
 * <p>Starts a real Elasticsearch container via {@link ElasticsearchTestResource},
 * uploads a template (which triggers indexing), then verifies that the search
 * endpoint returns hits.</p>
 */
@QuarkusTest
@QuarkusTestResource(value = ElasticsearchTestResource.class, restrictToAnnotatedClass = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SearchIT {

    private static String uploadedTemplateId;

    @Test
    @Order(1)
    void searchReturnsEmptyForShortQuery() {
        Response response = RestAssured.given()
                .queryParam("q", "a")
                .get("/api/workbench/search");

        assertEquals(200, response.statusCode());
        assertEquals(0, response.jsonPath().getLong("total"));
    }

    @Test
    @Order(2)
    void searchReturnsEmptyForUnknownTerm() {
        Response response = RestAssured.given()
                .queryParam("q", "XYZ_NONEXISTENT_9999")
                .get("/api/workbench/search");

        assertEquals(200, response.statusCode());
        assertEquals(0, response.jsonPath().getLong("total"));
    }

    @Test
    @Order(3)
    void uploadTemplateTriggersIndexing() throws Exception {
        byte[] odt = loadResource("kuendigung.odt");
        Path tmp = Files.createTempFile("search-it-", ".odt");
        Files.write(tmp, odt);

        Response response = RestAssured.given()
                .multiPart("name", "Kuendigung-SearchIT")
                .multiPart("file", tmp.toFile(), "application/vnd.oasis.opendocument.text")
                .post("/api/workbench/templates");

        assertEquals(201, response.statusCode());
        uploadedTemplateId = response.jsonPath().getString("id");
        assertNotNull(uploadedTemplateId);

        Files.deleteIfExists(tmp);

        // Give Elasticsearch a moment to make the document searchable
        Thread.sleep(1500);
    }

    @Test
    @Order(4)
    void searchFindsUploadedTemplate() {
        assertNotNull(uploadedTemplateId, "Upload in Order(3) must have succeeded");

        Response response = RestAssured.given()
                .queryParam("q", "Kuendigung")
                .get("/api/workbench/search");

        assertEquals(200, response.statusCode());
        assertTrue(response.jsonPath().getLong("total") >= 1,
                "Expected at least 1 hit for 'Kuendigung'");

        String firstName = response.jsonPath().getString("hits[0].name");
        assertNotNull(firstName);
    }

    @Test
    @Order(5)
    void searchFilterByTypeReturnsOnlyTemplates() {
        Response response = RestAssured.given()
                .queryParam("q", "Kuendigung")
                .queryParam("type", "TEMPLATE")
                .get("/api/workbench/search");

        assertEquals(200, response.statusCode());
        response.jsonPath().<String>getList("hits.type")
                .forEach(type -> assertEquals("TEMPLATE", type));
    }

    private byte[] loadResource(String name) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(is, "Test resource not found: " + name);
            return is.readAllBytes();
        }
    }
}