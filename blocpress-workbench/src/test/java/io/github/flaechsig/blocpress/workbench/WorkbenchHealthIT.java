package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for blocpress-workbench.
 * Quarkus Dev Services startet PostgreSQL automatisch via Testcontainers.
 * Tests laufen im selben Prozess – JaCoCo Coverage funktioniert ohne
 * zusätzliche Konfiguration.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkbenchIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static String uploadedTemplateId;

    // --- Health & Infrastructure ---

    @Test
    @Order(1)
    void healthEndpointReturnsUp() throws Exception {
        Response response = get("/q/health/ready");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().asString().contains("UP"));
    }

    @Test
    @Order(2)
    void workbenchWebComponentIsServed() {
        Response response = get("/components/bp-workbench.js");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().asString().contains("BpWorkbench"));
    }

    // --- Template CRUD ---

    @Test
    @Order(3)
    void listTemplatesInitiallyEmpty() throws Exception {
        Response response = get("/api/workbench/templates");

        assertEquals(200, response.statusCode());
        JsonNode list = MAPPER.readTree(response.body().asString());
        assertTrue(list.isArray());
        assertEquals(0, list.size());
    }

    @Test
    @Order(4)
    void uploadTemplate() throws Exception {
        byte[] odtContent = "fake-odt-content".getBytes(StandardCharsets.UTF_8);
        Response response = uploadMultipart("test-template", odtContent);

        assertEquals(201, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body().asString());
        assertNotNull(body.get("id"));
        assertEquals("test-template", body.get("name").asText());
        assertTrue(body.has("isValid"));
        uploadedTemplateId = body.get("id").asText();
    }

    @Test
    @Order(5)
    void uploadDuplicateNameReturnsConflict() {
        byte[] odtContent = "other-content".getBytes(StandardCharsets.UTF_8);
        Response response = uploadMultipart("test-template", odtContent);

        assertEquals(409, response.statusCode());
    }

    @Test
    @Order(6)
    void uploadWithoutNameReturnsBadRequest() {
        byte[] odtContent = "content".getBytes(StandardCharsets.UTF_8);
        Response response = uploadMultipart("", odtContent);

        assertEquals(400, response.statusCode());
    }

    @Test
    @Order(7)
    void listTemplatesAfterUpload() throws Exception {
        Response response = get("/api/workbench/templates");

        assertEquals(200, response.statusCode());
        JsonNode list = MAPPER.readTree(response.body().asString());
        assertTrue(list.isArray());
        assertEquals(1, list.size());
        assertEquals("test-template", list.get(0).get("name").asText());
    }

    @Test
    @Order(8)
    void downloadTemplate() {
        assertNotNull(uploadedTemplateId, "Upload must have succeeded first");
        Response response = get("/api/workbench/templates/" + uploadedTemplateId);

        assertEquals(200, response.statusCode());
        String contentDisposition = response.header("Content-Disposition");
        assertNotNull(contentDisposition);
        assertTrue(contentDisposition.contains("test-template"));
        assertEquals("fake-odt-content", response.body().asString());
    }

    @Test
    @Order(9)
    void downloadNonExistentTemplateReturns404() {
        Response response = get("/api/workbench/templates/" + UUID.randomUUID());

        assertEquals(404, response.statusCode());
    }

    // --- Template Details & Submit ---

    @Test
    @Order(10)
    void getTemplateDetails() throws Exception {
        assertNotNull(uploadedTemplateId, "Upload must have succeeded first");
        Response response = get("/api/workbench/templates/" + uploadedTemplateId + "/details");

        assertEquals(200, response.statusCode());
        JsonNode details = MAPPER.readTree(response.body().asString());
        assertNotNull(details.get("id"));
        assertEquals("test-template", details.get("name").asText());
        assertNotNull(details.get("status"));
        assertNotNull(details.get("validationResult"));
    }

    @Test
    @Order(11)
    void getDetailsOfNonExistentTemplate() {
        Response response = get("/api/workbench/templates/" + UUID.randomUUID() + "/details");

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(12)
    void submitTemplateForApproval() throws Exception {
        assertNotNull(uploadedTemplateId, "Upload must have succeeded first");
        Response response = post("/api/workbench/templates/" + uploadedTemplateId + "/submit", "");

        // Kann 200 oder 400 sein je nach Validierungsergebnis
        assertTrue(response.statusCode() == 200 || response.statusCode() == 400);

        if (response.statusCode() == 200) {
            JsonNode body = MAPPER.readTree(response.body().asString());
            assertNotNull(body.get("status"));
        }
    }

    @Test
    @Order(13)
    void submitNonExistentTemplate() {
        Response response = post("/api/workbench/templates/" + UUID.randomUUID() + "/submit", "");

        assertEquals(404, response.statusCode());
    }

    // --- Delete ---

    @Test
    @Order(14)
    void deleteTemplate() {
        assertNotNull(uploadedTemplateId, "Upload must have succeeded first");
        Response response = delete("/api/workbench/templates/" + uploadedTemplateId);

        assertEquals(204, response.statusCode());
    }

    @Test
    @Order(15)
    void deleteNonExistentTemplateReturns404() {
        Response response = delete("/api/workbench/templates/" + UUID.randomUUID());

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(16)
    void listTemplatesAfterDelete() throws Exception {
        Response response = get("/api/workbench/templates");

        assertEquals(200, response.statusCode());
        JsonNode list = MAPPER.readTree(response.body().asString());
        assertTrue(list.isArray());
        assertEquals(0, list.size());
    }

    // --- Weitere Szenarien ---

    @Test
    @Order(17)
    void uploadMultipleTemplates() throws Exception {
        for (int i = 0; i < 3; i++) {
            Response response = uploadMultipart("multi-template-" + i, ("template-" + i).getBytes());
            assertEquals(201, response.statusCode());
        }

        Response listResponse = get("/api/workbench/templates");
        assertEquals(200, listResponse.statusCode());
        JsonNode list = MAPPER.readTree(listResponse.body().asString());
        assertTrue(list.size() >= 3);
    }

    @Test
    @Order(18)
    void validationResultStructure() throws Exception {
        Response response = uploadMultipart("validation-structure", "test-validation".getBytes());

        assertEquals(201, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body().asString());
        assertNotNull(body.get("id"));
        assertNotNull(body.get("name"));
        assertTrue(body.has("isValid") || body.has("validationResult"));
    }

    // --- Hilfsmethoden ---

    private Response get(String path) {
        return RestAssured.given()
                .when()
                .get(path);
    }

    private Response delete(String path) {
        return RestAssured.given()
                .when()
                .delete(path);
    }

    private Response post(String path, String body) {
        return RestAssured.given()
                .body(body)
                .when()
                .post(path);
    }

    private Response uploadMultipart(String name, byte[] fileContent) {
        return RestAssured.given()
                .multiPart("name", name)
                .multiPart("file", "template.odt", fileContent,
                        "application/vnd.oasis.opendocument.text")
                .when()
                .post("/api/workbench/templates");
    }
}