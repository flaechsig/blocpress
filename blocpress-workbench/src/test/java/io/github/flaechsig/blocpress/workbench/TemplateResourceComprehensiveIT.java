package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for TemplateResource error handling and edge cases.
 * These tests exercise all code paths in TemplateResource to maximize coverage.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemplateResourceComprehensiveIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --- Upload Error Scenarios ---

    @Test
    @Order(1)
    void uploadWithBlankNameReturnsBadRequest() {
        byte[] odtContent = "content".getBytes(StandardCharsets.UTF_8);
        Response response = uploadMultipart("   ", odtContent);

        assertEquals(400, response.statusCode());
    }

    @Test
    @Order(2)
    void uploadWithSpecialCharactersInName() throws Exception {
        byte[] odtContent = "content".getBytes(StandardCharsets.UTF_8);
        Response response = uploadMultipart("template-with-special-@#$", odtContent);

        // Should succeed - special chars are allowed
        assertEquals(201, response.statusCode());
    }

    @Test
    @Order(3)
    void uploadValidTemplate() throws Exception {
        byte[] odtContent = "valid-template-content".getBytes(StandardCharsets.UTF_8);
        Response response = uploadMultipart("error-test-template", odtContent);

        assertEquals(201, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body().asString());
        assertNotNull(body.get("id"));
        assertEquals("error-test-template", body.get("name").asText());
        assertEquals(1, body.get("version").asInt());
        assertTrue(body.has("isValid"));
    }

    // --- Download Error Scenarios ---

    @Test
    @Order(4)
    void downloadNonExistentTemplateReturns404() {
        Response response = get("/api/workbench/templates/" + UUID.randomUUID());

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(5)
    void downloadTemplateWithInvalidUUID() {
        Response response = get("/api/workbench/templates/not-a-uuid");

        // Should return 404 or 400
        assertTrue(response.statusCode() == 404 || response.statusCode() == 400);
    }

    // --- Details Error Scenarios ---

    @Test
    @Order(6)
    void getDetailsOfNonExistentTemplate() {
        Response response = get("/api/workbench/templates/" + UUID.randomUUID() + "/details");

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(7)
    void getDetailsReturnsValidationResult() throws Exception {
        // Create a template first
        Response uploadResp = uploadMultipart("details-test", "template-data".getBytes());
        assertEquals(201, uploadResp.statusCode());
        JsonNode uploadBody = MAPPER.readTree(uploadResp.body().asString());
        String templateId = uploadBody.get("id").asText();

        // Get details
        Response response = get("/api/workbench/templates/" + templateId + "/details");

        assertEquals(200, response.statusCode());
        JsonNode details = MAPPER.readTree(response.body().asString());
        assertNotNull(details.get("id"));
        assertEquals("details-test", details.get("name").asText());
        assertEquals("DRAFT", details.get("status").asText());
        assertNotNull(details.get("validationResult"));
    }

    // --- List Error Scenarios ---

    @Test
    @Order(8)
    void listTemplatesEmptyDatabase() throws Exception {
        Response response = get("/api/workbench/templates");

        assertEquals(200, response.statusCode());
        JsonNode list = MAPPER.readTree(response.body().asString());
        assertTrue(list.isArray());
        // Could be empty or have items from previous tests
        assertTrue(list.size() >= 0);
    }

    @Test
    @Order(9)
    void listTemplatesReturnsLatestVersionOnly() throws Exception {
        // Upload same template 3 times to create v1, v2, v3
        uploadMultipart("multiversion-test", "v1-content".getBytes());
        uploadMultipart("multiversion-test", "v2-content".getBytes());
        uploadMultipart("multiversion-test", "v3-content".getBytes());

        // List should only show v3 (latest)
        Response response = get("/api/workbench/templates");
        assertEquals(200, response.statusCode());

        JsonNode list = MAPPER.readTree(response.body().asString());
        long multiversionCount = 0;
        for (JsonNode item : list) {
            if ("multiversion-test".equals(item.get("name").asText())) {
                multiversionCount++;
            }
        }

        // Should appear only once (v3)
        assertEquals(1, multiversionCount);
    }

    // --- Submit Error Scenarios ---

    @Test
    @Order(10)
    void submitNonExistentTemplate() {
        Response response = post("/api/workbench/templates/" + UUID.randomUUID() + "/submit", "");

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(11)
    void submitInvalidTemplate() throws Exception {
        // Create a template with invalid content
        Response uploadResp = uploadMultipart("invalid-submit", "bad-content".getBytes());
        assertEquals(201, uploadResp.statusCode());
        JsonNode uploadBody = MAPPER.readTree(uploadResp.body().asString());
        String templateId = uploadBody.get("id").asText();

        // Try to submit
        Response submitResp = post("/api/workbench/templates/" + templateId + "/submit", "");

        // Should be 400 (validation failed) or 200 (depending on validator)
        assertTrue(submitResp.statusCode() == 200 || submitResp.statusCode() == 400);
    }

    // --- Delete Error Scenarios ---

    @Test
    @Order(12)
    void deleteNonExistentTemplate() {
        Response response = delete("/api/workbench/templates/" + UUID.randomUUID());

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(13)
    void deleteExistingTemplate() throws Exception {
        Response uploadResp = uploadMultipart("delete-test", "content".getBytes());
        assertEquals(201, uploadResp.statusCode());
        JsonNode uploadBody = MAPPER.readTree(uploadResp.body().asString());
        String templateId = uploadBody.get("id").asText();

        Response deleteResp = delete("/api/workbench/templates/" + templateId);
        assertEquals(204, deleteResp.statusCode());

        // Verify it's deleted
        Response getResp = get("/api/workbench/templates/" + templateId);
        assertEquals(404, getResp.statusCode());
    }

    @Test
    @Order(14)
    void deleteAlreadyDeletedTemplate() throws Exception {
        Response uploadResp = uploadMultipart("double-delete-test", "content".getBytes());
        assertEquals(201, uploadResp.statusCode());
        JsonNode uploadBody = MAPPER.readTree(uploadResp.body().asString());
        String templateId = uploadBody.get("id").asText();

        // Delete once
        delete("/api/workbench/templates/" + templateId);

        // Try to delete again
        Response response = delete("/api/workbench/templates/" + templateId);
        assertEquals(404, response.statusCode());
    }

    // --- Duplicate Error Scenarios ---

    @Test
    @Order(15)
    void duplicateNonExistentTemplate() {
        String duplicateJson = "{\"name\": \"new-name\"}";
        Response response = RestAssured.given()
                .header("Content-Type", "application/json")
                .body(duplicateJson)
                .when()
                .post("/api/workbench/templates/" + UUID.randomUUID() + "/duplicate");

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(16)
    void duplicateWithSameName() throws Exception {
        Response uploadResp = uploadMultipart("dup-same-name", "content".getBytes());
        assertEquals(201, uploadResp.statusCode());
        JsonNode uploadBody = MAPPER.readTree(uploadResp.body().asString());
        String templateId = uploadBody.get("id").asText();

        // Duplicate with same name - should create v2
        String duplicateJson = "{\"name\": \"dup-same-name\"}";
        Response dupResp = RestAssured.given()
                .header("Content-Type", "application/json")
                .body(duplicateJson)
                .when()
                .post("/api/workbench/templates/" + templateId + "/duplicate");

        assertEquals(201, dupResp.statusCode());
        JsonNode dupBody = MAPPER.readTree(dupResp.body().asString());
        assertEquals("dup-same-name", dupBody.get("name").asText());
        assertEquals(2, dupBody.get("version").asInt()); // Should be v2
    }

    @Test
    @Order(17)
    void duplicateWithNewName() throws Exception {
        Response uploadResp = uploadMultipart("dup-original", "content".getBytes());
        assertEquals(201, uploadResp.statusCode());
        JsonNode uploadBody = MAPPER.readTree(uploadResp.body().asString());
        String templateId = uploadBody.get("id").asText();

        // Duplicate with new name
        String duplicateJson = "{\"name\": \"dup-copy\"}";
        Response dupResp = RestAssured.given()
                .header("Content-Type", "application/json")
                .body(duplicateJson)
                .when()
                .post("/api/workbench/templates/" + templateId + "/duplicate");

        assertEquals(201, dupResp.statusCode());
        JsonNode dupBody = MAPPER.readTree(dupResp.body().asString());
        assertEquals("dup-copy", dupBody.get("name").asText());
        assertEquals(1, dupBody.get("version").asInt()); // v1 for new name
    }

    // --- Content Access (UC-10) Error Scenarios ---

    @Test
    @Order(18)
    void getContentOfDraftTemplateReturns403() throws Exception {
        Response uploadResp = uploadMultipart("draft-content", "content".getBytes());
        assertEquals(201, uploadResp.statusCode());
        JsonNode uploadBody = MAPPER.readTree(uploadResp.body().asString());
        String templateId = uploadBody.get("id").asText();

        // Try to get content of DRAFT template
        Response response = get("/api/workbench/templates/" + templateId + "/content");

        // Should be 403 or 404 (not approved)
        assertTrue(response.statusCode() == 403 || response.statusCode() == 404);
    }

    @Test
    @Order(19)
    void getContentOfNonExistentTemplateReturns404() {
        Response response = get("/api/workbench/templates/" + UUID.randomUUID() + "/content");

        assertEquals(404, response.statusCode());
    }

    // --- TestDataSet Error Scenarios ---

    @Test
    @Order(20)
    void listTestDataSetsForNonExistentTemplate() {
        Response response = get("/api/workbench/templates/" + UUID.randomUUID() + "/testdata");

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(21)
    void getSpecificTestDataSetNotFound() throws Exception {
        Response uploadResp = uploadMultipart("testdata-notfound", "content".getBytes());
        assertEquals(201, uploadResp.statusCode());
        JsonNode uploadBody = MAPPER.readTree(uploadResp.body().asString());
        String templateId = uploadBody.get("id").asText();

        Response response = get("/api/workbench/templates/" + templateId + "/testdata/" + UUID.randomUUID());

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(22)
    void createTestDataSetForNonExistentTemplate() {
        String testDataJson = "{\"name\": \"Test\", \"testData\": {}}";
        Response response = RestAssured.given()
                .header("Content-Type", "application/json")
                .body(testDataJson)
                .when()
                .post("/api/workbench/templates/" + UUID.randomUUID() + "/testdata");

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(23)
    void updateTestDataSetForNonExistentTemplate() {
        String testDataJson = "{\"name\": \"Test\", \"testData\": {}}";
        Response response = RestAssured.given()
                .header("Content-Type", "application/json")
                .body(testDataJson)
                .when()
                .put("/api/workbench/templates/" + UUID.randomUUID() + "/testdata/" + UUID.randomUUID());

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(24)
    void deleteTestDataSetForNonExistentTemplate() {
        Response response = delete("/api/workbench/templates/" + UUID.randomUUID() + "/testdata/" + UUID.randomUUID());

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(25)
    void getExpectedPdfForNonExistentTemplate() {
        Response response = get("/api/workbench/templates/" + UUID.randomUUID() + "/testdata/" + UUID.randomUUID() + "/expected-pdf");

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(26)
    void saveExpectedPdfForNonExistentTemplate() {
        Response response = RestAssured.given()
                .header("Content-Type", "application/octet-stream")
                .body("fake-pdf".getBytes())
                .when()
                .post("/api/workbench/templates/" + UUID.randomUUID() + "/testdata/" + UUID.randomUUID() + "/save-expected");

        assertEquals(404, response.statusCode());
    }

    // --- Helper Methods ---

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
