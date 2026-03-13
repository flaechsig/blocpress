package io.github.flaechsig.blocpress.workbench;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the preview endpoint (POST /api/workbench/templates/{id}/preview).
 *
 * The render service is replaced by {@link MockRenderServerResource} so these tests
 * run without Docker / LibreOffice. Each test configures the mock before calling
 * the preview endpoint.
 *
 * Root cause of the 502 bug in the quickstart container:
 *   LibreOfficeProcessor throws {@code IllegalStateException} when the conversion
 *   fails (non-zero exit code). In RenderResource.renderDocumentJson the catch block
 *   only catches {@code IOException}, so the IllegalStateException propagates uncaught,
 *   causing Quarkus to return 500 with an HTML error page. The workbench converts any
 *   render response >= 400 into a 502 Bad Gateway.
 */
@QuarkusTest
@QuarkusTestResource(value = MockRenderServerResource.class, restrictToAnnotatedClass = true)
class PreviewIT {

    private static final String PREVIEW_JSON = """
            {"data": {"name": "Test", "vorname": "Max"}, "outputType": "pdf"}
            """;

    /** ID of a template uploaded in {@link #uploadTemplate()}. */
    private String templateId;

    @BeforeEach
    void uploadTemplate() throws Exception {
        byte[] odt = getClass().getClassLoader().getResourceAsStream("kuendigung.odt").readAllBytes();

        Response upload = RestAssured.given()
                .multiPart("name", "preview-test-" + System.nanoTime())
                .multiPart("file", "kuendigung.odt", odt,
                        "application/vnd.oasis.opendocument.text")
                .post("/api/workbench/templates");

        assertEquals(201, upload.statusCode(), "Upload must succeed before preview test");
        templateId = upload.jsonPath().getString("id");
        assertNotNull(templateId);
    }

    // -----------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------

    @Test
    void preview_returns_200_when_render_succeeds() {
        MockRenderServerResource.respondWithPdf();

        Response response = callPreview(templateId);

        assertEquals(200, response.statusCode());
        String contentType = response.header("Content-Type");
        assertNotNull(contentType);
        assertTrue(contentType.contains("application/pdf"),
                "Expected application/pdf content type, got: " + contentType);
        assertTrue(response.asByteArray().length > 0, "PDF body must not be empty");
    }

    // -----------------------------------------------------------------
    // Bug regression: render returns 500 → workbench must return 502
    // -----------------------------------------------------------------

    @Test
    void preview_returns_502_when_render_fails_with_500() {
        // This reproduces the quickstart 502: LibreOffice conversion failed →
        // IllegalStateException in render → uncaught → render returns 500 → workbench 502
        MockRenderServerResource.respondWithServerError("LibreOffice conversion failed (exit=1)");

        Response response = callPreview(templateId);

        assertEquals(502, response.statusCode(),
                "Workbench must return 502 when render returns 500. Body: " + response.asString());
        String body = response.asString();
        assertTrue(body.contains("502") || body.contains("Render service error") || body.contains("error"),
                "Error response must contain error info, got: " + body);
    }

    @Test
    void preview_returns_502_when_render_fails_with_422() {
        MockRenderServerResource.statusCode = 422;
        MockRenderServerResource.responseBody = "Validation failed".getBytes();
        MockRenderServerResource.responseContentType = "text/plain";

        Response response = callPreview(templateId);

        assertEquals(502, response.statusCode());
    }

    // -----------------------------------------------------------------
    // Not found
    // -----------------------------------------------------------------

    @Test
    void preview_returns_404_for_unknown_template() {
        MockRenderServerResource.respondWithPdf(); // mock doesn't matter — workbench should fail before calling render

        Response response = RestAssured.given()
                .contentType("application/json")
                .body(PREVIEW_JSON)
                .post("/api/workbench/templates/00000000-0000-0000-0000-000000000000/preview");

        assertEquals(404, response.statusCode());
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private Response callPreview(String id) {
        return RestAssured.given()
                .contentType("application/json")
                .body(PREVIEW_JSON)
                .post("/api/workbench/templates/" + id + "/preview");
    }
}
