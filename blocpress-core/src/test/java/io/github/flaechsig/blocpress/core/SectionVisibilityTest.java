package io.github.flaechsig.blocpress.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.odftoolkit.odfdom.doc.OdfTextDocument;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that text:section elements have the correct text:is-hidden attribute
 * after condition evaluation — LibreOffice respects this attribute to show/hide sections.
 */
public class SectionVisibilityTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final URI baseUri = Path.of(System.getProperty("user.dir"), "src/test/resources")
            .toAbsolutePath().toUri();

    @Test
    void sectionWithFalseConditionIsVisible() throws Exception {
        String json = """
                { "kunde": { "anrede": "FRAU", "nachname": "Müller" } }
                """;
        JsonNode data = mapper.readTree(json);
        byte[] rendered = RenderEngine.mergeTemplate(
                baseUri.resolve("section.odt").normalize().toURL(), data);

        try (var in = new ByteArrayInputStream(rendered)) {
            OdfTextDocument doc = OdfTextDocument.loadDocument(in);
            NodeList sections = doc.getContentRoot().getElementsByTagName("text:section");

            assertTrue(sections.getLength() > 0, "at least one section must remain");

            for (int i = 0; i < sections.getLength(); i++) {
                Element section = (Element) sections.item(i);
                String isHidden = section.getAttribute("text:is-hidden");
                assertNotEquals("true", isHidden,
                        "section '" + section.getAttribute("text:name")
                                + "' must not be hidden when its condition evaluated to false");
            }
        }
    }

    @Test
    void sectionWithTrueConditionIsRemoved() throws Exception {
        String json = """
                { "kunde": { "anrede": "FRAU", "nachname": "Müller" } }
                """;
        JsonNode data = mapper.readTree(json);
        byte[] rendered = RenderEngine.mergeTemplate(
                baseUri.resolve("section.odt").normalize().toURL(), data);

        try (var in = new ByteArrayInputStream(rendered)) {
            OdfTextDocument doc = OdfTextDocument.loadDocument(in);
            NodeList sections = doc.getContentRoot().getElementsByTagName("text:section");

            for (int i = 0; i < sections.getLength(); i++) {
                Element section = (Element) sections.item(i);
                // Section2 (condition: anrede != "HERR") evaluates to TRUE for FRAU → removed
                assertNotEquals("Section2", section.getAttribute("text:name"),
                        "Section2 should have been removed (condition was true)");
                // Section3 (condition: anrede == "FRAU" OR ...) evaluates to TRUE → removed
                assertNotEquals("Section3", section.getAttribute("text:name"),
                        "Section3 should have been removed (condition was true)");
            }
        }
    }
}
