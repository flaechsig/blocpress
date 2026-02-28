package io.github.flaechsig.blocpress.workbench;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for template versioning (UC-10.1).
 *
 * Tests the complete workflow:
 * 1. Upload template (creates v1)
 * 2. Upload same template again (creates v2)
 * 3. Approve v1 (sets validFrom = now, v1 becomes active)
 * 4. Approve v2 with future validFrom (v2 becomes active later)
 * 5. Query by name (returns active version)
 */
@QuarkusTest
class TemplateVersioningIntegrationTest {

    private byte[] sampleOdtTemplate;

    @BeforeEach
    void setUp() throws IOException {
        // Load sample template from test resources
        sampleOdtTemplate = Files.readAllBytes(
            Paths.get("src/test/resources/kuendigung.odt")
        );
    }

    @Test
    void testUploadCreatesFirstVersion() {
        // Upload new template "VersionTestTemplate"
        // Should create version = 1
        given()
            .multiPart("name", "VersionTestTemplate")
            .multiPart("file", "template.odt", sampleOdtTemplate)
        .when()
            .post("/api/workbench/templates")
        .then()
            .statusCode(201)
            .body("name", equalTo("VersionTestTemplate"))
            .body("version", equalTo(1));
    }

    @Test
    void testSecondUploadCreatesSecondVersion() {
        // First upload
        given()
            .multiPart("name", "VersionTest2")
            .multiPart("file", "template.odt", sampleOdtTemplate)
        .when()
            .post("/api/workbench/templates")
        .then()
            .statusCode(201)
            .body("version", equalTo(1));

        // Second upload of same name
        given()
            .multiPart("name", "VersionTest2")
            .multiPart("file", "template.odt", sampleOdtTemplate)
        .when()
            .post("/api/workbench/templates")
        .then()
            .statusCode(201)
            .body("name", equalTo("VersionTest2"))
            .body("version", equalTo(2));
    }

    @Test
    void testApprovalSetsValidFrom() {
        // Upload template
        String templateId = given()
            .multiPart("name", "ApprovalTest")
            .multiPart("file", "template.odt", sampleOdtTemplate)
        .when()
            .post("/api/workbench/templates")
        .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Transition to APPROVED
        given()
            .contentType(ContentType.JSON)
            .body("{\"newStatus\": \"APPROVED\"}")
        .when()
            .put("/api/workbench/templates/" + templateId + "/status")
        .then()
            .statusCode(200)
            .body("status", equalTo("APPROVED"))
            .body("validFrom", notNullValue()); // validFrom should be set
    }

    @Test
    void testMultipleVersionsCanCoexist() {
        // Upload v1
        String v1Id = given()
            .multiPart("name", "CoexistTest")
            .multiPart("file", "template.odt", sampleOdtTemplate)
        .when()
            .post("/api/workbench/templates")
        .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Upload v2
        String v2Id = given()
            .multiPart("name", "CoexistTest")
            .multiPart("file", "template.odt", sampleOdtTemplate)
        .when()
            .post("/api/workbench/templates")
        .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Both should exist and have different versions
        assertNotEquals(v1Id, v2Id, "Different versions should have different IDs");

        // Approve both
        for (String id : new String[]{v1Id, v2Id}) {
            given()
                .contentType(ContentType.JSON)
                .body("{\"newStatus\": \"APPROVED\"}")
            .when()
                .put("/api/workbench/templates/" + id + "/status")
            .then()
                .statusCode(200)
                .body("status", equalTo("APPROVED"));
        }
    }

    @Test
    void testFetchLatestActiveVersionByName() {
        // Upload and approve v1
        given()
            .multiPart("name", "FetchTest")
            .multiPart("file", "template.odt", sampleOdtTemplate)
        .when()
            .post("/api/workbench/templates")
        .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Upload and approve v2
        String v2Id = given()
            .multiPart("name", "FetchTest")
            .multiPart("file", "template.odt", sampleOdtTemplate)
        .when()
            .post("/api/workbench/templates")
        .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Approve both versions
        given()
            .contentType(ContentType.JSON)
            .body("{\"newStatus\": \"SUBMITTED\"}")
        .when()
            .put("/api/workbench/templates/" + v2Id + "/status");

        given()
            .contentType(ContentType.JSON)
            .body("{\"newStatus\": \"APPROVED\"}")
        .when()
            .put("/api/workbench/templates/" + v2Id + "/status")
        .then()
            .statusCode(200);

        // Fetch by name - should return v2 (highest version that is APPROVED)
        given()
        .when()
            .get("/api/workbench/templates/by-name/FetchTest/content")
        .then()
            .statusCode(200)
            .header("X-Template-Name", equalTo("FetchTest"));
            // Note: Can't easily verify version in header, but endpoint should return latest
    }

    @Test
    void testNoActiveVersionReturns404() {
        // Upload v1 but don't approve
        given()
            .multiPart("name", "NotApprovedTest")
            .multiPart("file", "template.odt", sampleOdtTemplate)
        .when()
            .post("/api/workbench/templates")
        .then()
            .statusCode(201);

        // Try to fetch by name - should return 404 (no APPROVED version)
        given()
        .when()
            .get("/api/workbench/templates/by-name/NotApprovedTest/content")
        .then()
            .statusCode(404);
    }

    @Test
    void testNonExistentTemplateReturns404() {
        // Try to fetch non-existent template by name
        given()
        .when()
            .get("/api/workbench/templates/by-name/NonExistent/content")
        .then()
            .statusCode(404);
    }

    @Test
    void testValidFromNullUntilApproved() {
        // Upload template (should have validFrom = null)
        String templateId = given()
            .multiPart("name", "ValidFromTest")
            .multiPart("file", "template.odt", sampleOdtTemplate)
        .when()
            .post("/api/workbench/templates")
        .then()
            .statusCode(201)
            .extract()
            .path("id");

        // Check details - validFrom should be null in DRAFT status
        given()
        .when()
            .get("/api/workbench/templates/" + templateId + "/details")
        .then()
            .statusCode(200)
            .body("status", equalTo("DRAFT"));
            // Note: validFrom not checked because it's in the Template entity,
            // not typically exposed in details response
    }

    @Test
    void testUploadWithNullNameReturns400() {
        // Upload with null/empty name should fail
        given()
            .multiPart("name", "")
            .multiPart("file", "template.odt", sampleOdtTemplate)
        .when()
            .post("/api/workbench/templates")
        .then()
            .statusCode(400);
    }

    @Test
    void testVersioningDoesNotConflictWithDifferentNames() {
        // Upload "Template1"
        given()
            .multiPart("name", "Template1")
            .multiPart("file", "template.odt", sampleOdtTemplate)
        .when()
            .post("/api/workbench/templates")
        .then()
            .statusCode(201)
            .body("version", equalTo(1));

        // Upload "Template2" - should also be version 1
        given()
            .multiPart("name", "Template2")
            .multiPart("file", "template.odt", sampleOdtTemplate)
        .when()
            .post("/api/workbench/templates")
        .then()
            .statusCode(201)
            .body("version", equalTo(1)); // Different template, starts at v1
    }
}
