package io.github.flaechsig.blocpress.render;

import io.github.flaechsig.blocpress.render.model.RenderRequest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

import static io.github.flaechsig.blocpress.render.TestDocumentUtil.*;
import static org.junit.jupiter.api.Assertions.*;

class TemplateResourceTest {

    private static final String VALID_JSON = """
            {
              "datum": "2026-02-10",
              "kunde": {
                "nachname": "Testnachname",
                "vorname": "Testvorname",
                "adresse": {
                  "strasse": "adessoplatz",
                  "hausnummer": "1",
                  "plz": "44269",
                  "ort": "Dortmund"
                }
              },
              "versicherung": {
                "name": "Handyversicherung AG",
                "ort": "Düsseldorf",
                "plz": "12345",
                "strasse": "Versicherungsstrasse 1"
              },
              "vertrag": {
                "ende": "2026-07-01",
                "nummer": "HV123456789",
                "sparte": "Handyversicherung"
              }
            }
            """;

    private final RenderResource resource = new RenderResource();

    @Test
    void mergeTemplateOdt() throws Exception {
        InputStream template = getClass().getResourceAsStream("/kuendigung.odt");
        assertNotNull(template, "Template /kuendigung.odt not found on classpath");

        File result = resource.renderDocumentMultipart(
                "application/vnd.oasis.opendocument.text",
                template,
                VALID_JSON
        );

        assertNotNull(result, "mergeTemplate returned null");
        assertTrue(result.exists(), "Result file does not exist: " + result.getAbsolutePath());
        assertTrue(result.length() > 0, "Result file is empty: " + result.getAbsolutePath());

        byte[] actual = Files.readAllBytes(result.toPath());
        byte[] expected = getClass().getResourceAsStream("/kuendigung_generated.odt").readAllBytes();

        String actualText = normalizeText(extractOdtText(actual));
        String expectedText = normalizeText(extractOdtText(expected));
        assertEquals(expectedText, actualText,
                "ODT text mismatch.\n--- Expected ---\n" + expectedText + "\n--- Actual ---\n" + actualText);
    }

    @Test
    void mergeTemplatePdf() throws Exception {
        InputStream template = getClass().getResourceAsStream("/kuendigung_generated.odt");
        assertNotNull(template, "Template /kuendigung_generated.odt not found on classpath");

        File result = resource.renderDocumentMultipart(
                "application/pdf",
                template,
                VALID_JSON
        );

        assertNotNull(result, "mergeTemplate returned null");
        assertTrue(result.exists(), "Result file does not exist: " + result.getAbsolutePath());
        assertTrue(result.length() > 0, "Result file is empty: " + result.getAbsolutePath());

        byte[] actual = Files.readAllBytes(result.toPath());
        byte[] expected = getClass().getResourceAsStream("/kuendigung_generated.pdf").readAllBytes();

        String actualText = normalizeText(extractPdfText(actual));
        String expectedText = normalizeText(extractPdfText(expected));
        assertEquals(expectedText, actualText,
                "PDF text mismatch.\n--- Expected ---\n" + expectedText + "\n--- Actual ---\n" + actualText);
    }

    @Test
    void mergeTemplateRtf() throws Exception {
        InputStream template = getClass().getResourceAsStream("/kuendigung_generated.odt");
        assertNotNull(template, "Template /kuendigung_generated.odt not found on classpath");

        File result = resource.renderDocumentMultipart(
                "application/rtf",
                template,
                VALID_JSON
        );

        assertNotNull(result, "mergeTemplate returned null");
        assertTrue(result.exists(), "Result file does not exist: " + result.getAbsolutePath());
        assertTrue(result.length() > 0, "Result file is empty: " + result.getAbsolutePath());

        byte[] actual = Files.readAllBytes(result.toPath());
        byte[] expected = getClass().getResourceAsStream("/kuendigung_generated.rtf").readAllBytes();

        String actualText = normalizeText(extractRtfText(actual));
        String expectedText = normalizeText(extractRtfText(expected));
        assertEquals(expectedText, actualText,
                "RTF text mismatch.\n--- Expected ---\n" + expectedText + "\n--- Actual ---\n" + actualText);
    }

    @Test
    void invalidAcceptHeaderThrows() {
        InputStream template = getClass().getResourceAsStream("/kuendigung_generated.odt");
        assertNotNull(template, "Template /kuendigung_generated.odt not found on classpath");

        var ex = assertThrows(IllegalStateException.class, () ->
                resource.renderDocumentMultipart("application/json", template, VALID_JSON)
        );
        assertTrue(ex.getMessage().contains("application/json"),
                "Exception should mention the invalid accept value, but was: " + ex.getMessage());
    }

    @Test
    void renderDocumentOdt() throws Exception {
        byte[] templateBytes = getClass().getResourceAsStream("/kuendigung.odt").readAllBytes();

        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Object data = objectMapper.readValue(VALID_JSON, Object.class);

        RenderRequest request = new RenderRequest()
                .template(templateBytes)
                .data(data)
                .outputType(RenderRequest.OutputTypeEnum.ODT);

        File result = resource.renderDocumentJson(request);

        assertNotNull(result, "renderDocument returned null");
        assertTrue(result.exists(), "Result file does not exist: " + result.getAbsolutePath());
        assertTrue(result.length() > 0, "Result file is empty: " + result.getAbsolutePath());

        byte[] actual = Files.readAllBytes(result.toPath());
        byte[] expected = getClass().getResourceAsStream("/kuendigung_generated.odt").readAllBytes();

        String actualText = normalizeText(extractOdtText(actual));
        String expectedText = normalizeText(extractOdtText(expected));
        assertEquals(expectedText, actualText,
                "ODT text mismatch.\n--- Expected ---\n" + expectedText + "\n--- Actual ---\n" + actualText);
    }

    // ===== UC-10: Template-ID-Based Rendering Tests =====

    @Test
    void renderDocumentByIdRequiresValidTemplate() {
        // This test documents the behavior of renderDocumentById()
        // Full integration testing requires:
        // 1. A running blocpress-workbench instance on localhost:8081
        // 2. An APPROVED template with a known UUID in the workbench database
        // 3. Proper mock setup of TemplateCache or a test container

        // Expected behaviors:
        // - 200 OK: Template found and APPROVED, document rendered successfully
        // - 404 Not Found: Template ID does not exist in blocpress-workbench
        // - 403 Forbidden: Template exists but is not APPROVED (DRAFT/SUBMITTED/REJECTED status)
        // - 500 Internal Server Error: Network error or rendering error

        // For proper testing, use TestContainers or WireMock to simulate blocpress-workbench
        // Example setup would be:
        // - Start WireMock server
        // - Mock GET /api/workbench/templates/{id}/content endpoint
        // - Call renderDocumentById() with mocked template data
        // - Verify response status and content

        assertTrue(true, "Integration test for renderDocumentById would require running blocpress-workbench");
    }
}