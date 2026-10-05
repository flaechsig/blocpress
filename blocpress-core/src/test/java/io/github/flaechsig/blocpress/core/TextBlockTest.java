package io.github.flaechsig.blocpress.core;

import org.junit.jupiter.api.DisplayName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.flaechsig.blocpress.util.ResourceUtil.extractOdtContent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TextBlockTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI baseUri = Path.of(System.getProperty("user.dir"), "src/test/resources")
            .toAbsolutePath()
            .toUri();

    private static final String JSON = """           
                {
                      "customer": [
                          {
                            "gender": "MALE",
                            "firstName": "Michael",
                            "lastName": "Miller",
                            "address": {
                              "street": "Example Street 1",
                              "postcode": "12345",
                              "city": "Hamburg"
                            },
                            "contact": {
                              "email": "michael.miller@example.com"
                            }
                          },
                          {
                            "gender": "FEMALE",
                            "firstName": "Mini",
                            "lastName": "Müller",
                            "address": {
                              "street": "Main Street 1",
                              "postcode": "54321",
                              "city": "Munich"
                            },
                            "contact": {
                              "email": "mini.mueller@example.com"
                            }
                          }
                        ]
                    }
                """;

    /**
     * REQ-0011: sample-05.odt bindet special_agreement.odt als verknuepften Bereich ein; der Bereichsname
     * {@code SpecialAgreement(firstname=customer.0.firstName, lastname=customer.0.lastName)} ordnet die
     * Felder des Bausteins JSON-Pfaden zu. Im Baustein steht als Beispiel "Max Mustermann".
     */
    @Test
    @DisplayName("REQ-0011: testTextBlock")
    public void testTextBlock() throws Exception {
        JsonNode node = mapper.readTree(JSON);
        String text = extractOdtContent(render(node));

        assertTrue(text.contains("Special Agreement with Michael Miller"), "Baustein nicht eingebunden/befuellt: " + text);
        assertTrue(text.contains("Lorem ipsum"), "Bausteininhalt fehlt: " + text);
        assertFalse(text.contains("Max Mustermann"), "Beispielwert des Bausteins nicht ersetzt: " + text);
        // Rumpf der Hauptvorlage bleibt intakt (Schleife + Felder)
        assertTrue(text.contains("Mini Müller, Main Street 1, 54321 Munich"), text);
    }

    @Test
    @DisplayName("REQ-0011: textBlockAppearsInPdf")
    @EnabledIf("io.github.flaechsig.blocpress.core.TransformTest#sofficeAvailable")
    public void textBlockAppearsInPdf() throws Exception {
        byte[] pdf = LibreOfficeProcessor.refreshAndTransform(render(mapper.readTree(JSON)), OutputFormat.PDF);
        String text;
        try (var doc = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
        }
        assertTrue(text.contains("Special Agreement with Michael Miller"), "Baustein fehlt im PDF: " + text);
        assertFalse(text.contains("Max Mustermann"), text);
    }

    private byte[] render(JsonNode node) throws Exception {
        return RenderEngine.mergeTemplate(baseUri.resolve("sample-05.odt").normalize().toURL(), node);
    }
}
