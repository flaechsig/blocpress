package io.github.flaechsig.blocpress.render.template;

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

    private final TemplateResource resource = new TemplateResource();

    @Test
    void mergeTemplateOdt() throws Exception {
        InputStream template = getClass().getResourceAsStream("/kuendigung.odt");
        assertNotNull(template, "Template /kuendigung.odt not found on classpath");

        File result = resource.generateDocument(
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

        File result = resource.generateDocument(
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

        File result = resource.generateDocument(
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
                resource.generateDocument("application/json", template, VALID_JSON)
        );
        assertTrue(ex.getMessage().contains("application/json"),
                "Exception should mention the invalid accept value, but was: " + ex.getMessage());
    }
}