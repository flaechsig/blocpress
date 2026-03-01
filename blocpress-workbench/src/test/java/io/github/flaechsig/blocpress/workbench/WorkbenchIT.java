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
    void uploadDuplicateNameCreatesNewVersion() throws Exception {
        byte[] odtContent = "other-content".getBytes(StandardCharsets.UTF_8);
        Response response = uploadMultipart("test-template", odtContent);

        // With UC-10.1 versioning, duplicate names create new versions (v2, v3, etc.)
        assertEquals(201, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body().asString());
        assertEquals("test-template", body.get("name").asText());
        assertEquals(2, body.get("version").asInt()); // Should be v2
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
        assertEquals(1, list.size()); // Only latest version per name is shown
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
    void deleteTemplate() throws Exception {
        assertNotNull(uploadedTemplateId, "Upload must have succeeded first");
        Response response = delete("/api/workbench/templates/" + uploadedTemplateId);

        assertEquals(204, response.statusCode());

        // Store v2 ID for deletion in next test
        Response listResponse = get("/api/workbench/templates");
        JsonNode list = MAPPER.readTree(listResponse.body().asString());
        if (list.size() > 0) {
            uploadedTemplateId = list.get(0).get("id").asText(); // Get v2's ID
        }
    }

    @Test
    @Order(15)
    void deleteNonExistentTemplateReturns404() {
        Response response = delete("/api/workbench/templates/" + UUID.randomUUID());

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(16)
    void listTemplatesAfterDeleteOneVersion() throws Exception {
        Response response = get("/api/workbench/templates");

        assertEquals(200, response.statusCode());
        JsonNode list = MAPPER.readTree(response.body().asString());
        assertTrue(list.isArray());
        // v2 still exists as the latest version
        assertEquals(1, list.size());
    }

    @Test
    @Order(17)
    void deleteRemainingVersion() throws Exception {
        // Delete v2 to fully remove the template
        if (uploadedTemplateId != null) {
            Response response = delete("/api/workbench/templates/" + uploadedTemplateId);
            assertEquals(204, response.statusCode());
        }
    }

    @Test
    @Order(18)
    void listTemplatesAfterDeleteAllVersions() throws Exception {
        Response response = get("/api/workbench/templates");

        assertEquals(200, response.statusCode());
        JsonNode list = MAPPER.readTree(response.body().asString());
        assertTrue(list.isArray());
        // All versions deleted - list should be empty
        assertEquals(0, list.size());
    }

    // --- Status Management (UC-10.1) ---

    @Test
    @Order(19)
    void updateTemplateStatus() throws Exception {
        // Upload and update status of a fresh template
        Response uploadResp = uploadMultipart("status-template", "template-content".getBytes());
        assertEquals(201, uploadResp.statusCode());
        JsonNode uploadBody = MAPPER.readTree(uploadResp.body().asString());
        String templateId = uploadBody.get("id").asText();

        // Update status to SUBMITTED
        String statusJson = "{\"newStatus\": \"SUBMITTED\"}";
        Response response = RestAssured.given()
                .header("Content-Type", "application/json")
                .body(statusJson)
                .when()
                .post("/api/workbench/templates/" + templateId + "/submit");

        // Can be 200 or 400 depending on validation
        assertTrue(response.statusCode() == 200 || response.statusCode() == 400);
    }

    @Test
    @Order(20)
    void getTemplateContentByIdApprovedOnly() throws Exception {
        // Upload a template
        Response uploadResp = uploadMultipart("content-template", "odt-content-data".getBytes());
        assertEquals(201, uploadResp.statusCode());
        JsonNode uploadBody = MAPPER.readTree(uploadResp.body().asString());
        String templateId = uploadBody.get("id").asText();

        // Try to fetch content while DRAFT - should get 403 or 404
        Response response = get("/api/workbench/templates/" + templateId + "/content");
        assertTrue(response.statusCode() == 403 || response.statusCode() == 404);
    }

    @Test
    @Order(21)
    void duplicateTemplate() throws Exception {
        // Upload original template
        Response uploadResp = uploadMultipart("duplicate-source", "template-data".getBytes());
        assertEquals(201, uploadResp.statusCode());
        JsonNode uploadBody = MAPPER.readTree(uploadResp.body().asString());
        String originalId = uploadBody.get("id").asText();

        // Duplicate with new name
        String duplicateJson = "{\"name\": \"duplicate-target\"}";
        Response response = RestAssured.given()
                .header("Content-Type", "application/json")
                .body(duplicateJson)
                .when()
                .post("/api/workbench/templates/" + originalId + "/duplicate");

        assertEquals(201, response.statusCode());
        JsonNode dupBody = MAPPER.readTree(response.body().asString());
        assertEquals("duplicate-target", dupBody.get("name").asText());
        assertNotNull(dupBody.get("id"));
    }

    @Test
    @Order(22)
    void getSpecificTestDataSet() throws Exception {
        // Upload template for testdata
        Response uploadResp = uploadMultipart("testdata-get-template", "content".getBytes());
        assertEquals(201, uploadResp.statusCode());
        JsonNode uploadBody = MAPPER.readTree(uploadResp.body().asString());
        String templateId = uploadBody.get("id").asText();

        // Create test data set
        String testDataJson = "{\"name\": \"GetTest\", \"testData\": {\"field\": \"value\"}}";
        Response createResp = RestAssured.given()
                .header("Content-Type", "application/json")
                .body(testDataJson)
                .when()
                .post("/api/workbench/templates/" + templateId + "/testdata");

        assertEquals(201, createResp.statusCode());
        JsonNode createBody = MAPPER.readTree(createResp.body().asString());
        String testDataSetId = createBody.get("id").asText();

        // Fetch specific test data set
        Response response = get("/api/workbench/templates/" + templateId + "/testdata/" + testDataSetId);
        assertEquals(200, response.statusCode());
        JsonNode getBody = MAPPER.readTree(response.body().asString());
        assertEquals("GetTest", getBody.get("name").asText());
        assertNotNull(getBody.get("testData"));
    }

    // --- Weitere Szenarien ---

    @Test
    @Order(23)
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
    @Order(24)
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

    // --- TestDataSet CRUD ---

    private static String testDataSetId;

    @Test
    @Order(25)
    void listTestDataSetsInitiallyEmpty() throws Exception {
        // Upload a template first for TestDataSet tests
        Response uploadResp = uploadMultipart("testdata-template", "template-content".getBytes());
        assertEquals(201, uploadResp.statusCode());
        JsonNode uploadBody = MAPPER.readTree(uploadResp.body().asString());
        String templateId = uploadBody.get("id").asText();

        Response response = get("/api/workbench/templates/" + templateId + "/testdata");

        assertEquals(200, response.statusCode());
        JsonNode list = MAPPER.readTree(response.body().asString());
        assertTrue(list.isArray());
        assertEquals(0, list.size());
    }

    @Test
    @Order(26)
    void createTestDataSet() throws Exception {
        // Get the template from previous test
        Response templatesResp = get("/api/workbench/templates");
        JsonNode templates = MAPPER.readTree(templatesResp.body().asString());
        String templateId = templates.get(templates.size() - 1).get("id").asText();

        // Create TestDataSet
        String testDataJson = "{\"name\": \"Test1\", \"testData\": {\"firstname\": \"John\", \"lastname\": \"Doe\"}}";
        Response response = RestAssured.given()
                .header("Content-Type", "application/json")
                .body(testDataJson)
                .when()
                .post("/api/workbench/templates/" + templateId + "/testdata");

        assertEquals(201, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body().asString());
        assertNotNull(body.get("id"));
        assertEquals("Test1", body.get("name").asText());
        testDataSetId = body.get("id").asText();
    }

    @Test
    @Order(27)
    void listTestDataSetsAfterCreate() throws Exception {
        Response templatesResp = get("/api/workbench/templates");
        JsonNode templates = MAPPER.readTree(templatesResp.body().asString());
        String templateId = templates.get(templates.size() - 1).get("id").asText();

        Response response = get("/api/workbench/templates/" + templateId + "/testdata");

        assertEquals(200, response.statusCode());
        JsonNode list = MAPPER.readTree(response.body().asString());
        assertTrue(list.isArray());
        assertEquals(1, list.size());
        assertEquals("Test1", list.get(0).get("name").asText());
    }

    @Test
    @Order(28)
    void updateTestDataSet() throws Exception {
        Response templatesResp = get("/api/workbench/templates");
        JsonNode templates = MAPPER.readTree(templatesResp.body().asString());
        String templateId = templates.get(templates.size() - 1).get("id").asText();

        String updatedDataJson = "{\"name\": \"Test1Updated\", \"testData\": {\"firstname\": \"Jane\", \"lastname\": \"Smith\"}}";
        Response response = RestAssured.given()
                .header("Content-Type", "application/json")
                .body(updatedDataJson)
                .when()
                .put("/api/workbench/templates/" + templateId + "/testdata/" + testDataSetId);

        assertEquals(200, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body().asString());
        assertEquals("Test1Updated", body.get("name").asText());
    }

    @Test
    @Order(29)
    void saveExpectedPdf() throws Exception {
        Response templatesResp = get("/api/workbench/templates");
        JsonNode templates = MAPPER.readTree(templatesResp.body().asString());
        String templateId = templates.get(templates.size() - 1).get("id").asText();

        byte[] pdfContent = "fake-pdf-content".getBytes();
        Response response = RestAssured.given()
                .header("Content-Type", "application/octet-stream")
                .body(pdfContent)
                .when()
                .post("/api/workbench/templates/" + templateId + "/testdata/" + testDataSetId + "/save-expected");

        assertEquals(200, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body().asString());
        assertTrue(body.has("hash"));
        assertNotNull(body.get("hash").asText());
    }

    @Test
    @Order(30)
    void getExpectedPdf() throws Exception {
        Response templatesResp = get("/api/workbench/templates");
        JsonNode templates = MAPPER.readTree(templatesResp.body().asString());
        String templateId = templates.get(templates.size() - 1).get("id").asText();

        Response response = get("/api/workbench/templates/" + templateId + "/testdata/" + testDataSetId + "/expected-pdf");

        assertEquals(200, response.statusCode());
        byte[] content = response.body().asByteArray();
        assertEquals("fake-pdf-content", new String(content));
    }

    @Test
    @Order(31)
    void deleteTestDataSet() throws Exception {
        Response templatesResp = get("/api/workbench/templates");
        JsonNode templates = MAPPER.readTree(templatesResp.body().asString());
        String templateId = templates.get(templates.size() - 1).get("id").asText();

        Response response = delete("/api/workbench/templates/" + templateId + "/testdata/" + testDataSetId);

        assertEquals(204, response.statusCode());
    }

    @Test
    @Order(32)
    void listTestDataSetsAfterDelete() throws Exception {
        Response templatesResp = get("/api/workbench/templates");
        JsonNode templates = MAPPER.readTree(templatesResp.body().asString());
        String templateId = templates.get(templates.size() - 1).get("id").asText();

        Response response = get("/api/workbench/templates/" + templateId + "/testdata");

        assertEquals(200, response.statusCode());
        JsonNode list = MAPPER.readTree(response.body().asString());
        assertTrue(list.isArray());
        assertEquals(0, list.size());
    }
}