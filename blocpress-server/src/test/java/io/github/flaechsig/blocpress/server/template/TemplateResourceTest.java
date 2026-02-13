package io.github.flaechsig.blocpress.server.template;

import io.github.flaechsig.blocpress.server.TestDocumentUtil;
import io.github.flaechsig.blocpress.server.render.template.TemplateResource;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static io.github.flaechsig.blocpress.server.TestDocumentUtil.*;
import static java.text.Normalizer.normalize;
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

        assertNotNull(template, "Template ODT not found on classpath");

        File result = resource.mergeTemplate(
                "application/vnd.oasis.opendocument.text",
                template,
                VALID_JSON
        );
        byte[] expected = getClass().getResourceAsStream("/kuendigung_generated.odt").readAllBytes();
        byte[] actual = Files.readAllBytes(result.toPath());

        assertNotNull(result);
        assertTrue(result.exists());
        assertTrue(result.length() > 0, "Result file is empty");
        assertEquals(normalizeText(extractOdtText(expected)), normalizeText(extractOdtText(actual)));
    }

    @Test
    void mergeTemplatePdf() throws Exception {
        InputStream template = getClass().getResourceAsStream("/kuendigung_generated.odt");
        assertNotNull(template, "Template ODT not found on classpath");

        File result = resource.mergeTemplate(
                "application/pdf",
                template,
                VALID_JSON
        );
        byte[] expected = getClass().getResourceAsStream("/kuendigung_generated.pdf").readAllBytes();
        byte[] actual = Files.readAllBytes(result.toPath());

        assertNotNull(result);
        assertTrue(result.exists());
        assertTrue(result.length() > 0, "Result file is empty");
        assertEquals(normalizeText(extractPdfText(expected)), normalizeText(extractPdfText(actual)));
    }

    @Test
    void mergeTemplateRtf() throws Exception {
        InputStream template = getClass().getResourceAsStream("/kuendigung_generated.odt");
        assertNotNull(template, "Template ODT not found on classpath");

        File result = resource.mergeTemplate(
                "application/rtf",
                template,
                VALID_JSON
        );
        byte[] expected = getClass().getResourceAsStream("/kuendigung_generated.rtf").readAllBytes();
        byte[] actual = Files.readAllBytes(result.toPath());

        assertNotNull(result);
        assertTrue(result.exists());
        assertTrue(result.length() > 0, "Result file is empty");
        assertEquals(normalizeText(extractRtfText(expected)), normalizeText(extractRtfText(actual)));
    }

    @Test
    void invalidAcceptHeaderThrows() {
        InputStream template = getClass().getResourceAsStream("/kuendigung_generated.odt");
        assertNotNull(template, "Template ODT not found on classpath");

        assertThrows(IllegalStateException.class, () ->
                resource.mergeTemplate("application/json", template, VALID_JSON)
        );
    }
}
