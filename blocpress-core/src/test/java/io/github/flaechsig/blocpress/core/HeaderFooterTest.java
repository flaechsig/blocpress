package io.github.flaechsig.blocpress.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static io.github.flaechsig.blocpress.util.ResourceUtil.extractOdtContent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Platzhalter und Bedingungen in Kopf-/Fusszeilen (styles.xml, office:master-styles).
 *
 * <p>{@code header_footer.odt}: Master-Page "Standard" mit header, footer (Zahl, Referenz,
 * bedingter Text, Bereich mit Bedingung) und footer-left; Master-Page "First Page" (Seite 1)
 * mit eigenem header/footer. Die Fusszeilen-Zahl nutzt ein Format "N1" aus styles.xml
 * (gruppiert) — gleichnamig mit einem ungruppierten "N1" in content.xml.</p>
 */
class HeaderFooterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI baseUri = Path.of(System.getProperty("user.dir"), "src/test/resources").toAbsolutePath().toUri();

    @Test
    @Tag("REQ-0010")
    void fieldsAndConditionsInAllHeadersAndFootersAreResolved() throws Exception {
        byte[] odt = render("{\"offer\":{\"reference\":\"ANG-2026-0815\",\"supportId\":\"SUP-77\"},\"numbertest\":500000,\"kunde\":{\"anrede\":\"FRAU\"}}",
                Locale.GERMANY);
        Map<String, String> hf = headersAndFooters(odt);

        assertEquals("KOPF-ERSTE ANG-2026-0815", hf.get("First_20_Page/header"));
        assertEquals("FUSS-ERSTE 500.000", hf.get("First_20_Page/footer"));
        assertEquals("KOPF-STD ANG-2026-0815", hf.get("Standard/header"));
        assertEquals("FUSS-LINKS 500.000 ANG-2026-0815 SUP-77", hf.get("Standard/footer-left"));
        String footer = hf.get("Standard/footer");
        assertTrue(footer.startsWith("FUSS-RECHTS 500.000 ANG-2026-0815"), footer);
        assertTrue(footer.contains("FUSS-ANREDE Frau"), footer);
        assertTrue(footer.contains("FUSS-BEREICH-NUR-POSITIV"), "Bereich (Hide-Bedingung falsch) muss bleiben: " + footer);
        assertFalse(styles(odt).contains("user-field-get"), "Platzhalter in styles.xml nicht ersetzt");
        assertFalse(styles(odt).contains(" is-hidden="), "Attribut ohne Namensraum (ungueltiges ODF)");
    }

    @Test
    @Tag("REQ-0010")
    void sameFieldInBodyAndFooterGetsSameValue() throws Exception {
        byte[] odt = render("{\"offer\":{\"reference\":\"ANG-2026-0815\",\"supportId\":\"SUP-77\"},\"numbertest\":500000,\"kunde\":{\"anrede\":\"FRAU\"}}",
                Locale.GERMANY);
        String body = extractOdtContent(odt);
        assertTrue(body.contains("RUMPF-REF ANG-2026-0815"), body);
        assertTrue(body.contains("RUMPF-ZAHL 500.000"), body);
        assertTrue(headersAndFooters(odt).get("Standard/footer").contains("ANG-2026-0815"));
    }

    @Test
    @Tag("REQ-0010")
    void conditionsInFooterEvaluateOtherBranch() throws Exception {
        byte[] odt = render("{\"offer\":{\"reference\":\"X\"},\"numbertest\":-5,\"kunde\":{\"anrede\":\"HERR\"}}", Locale.GERMANY);
        String footer = headersAndFooters(odt).get("Standard/footer");
        assertTrue(footer.contains("FUSS-ANREDE Herr"), footer);
        assertFalse(footer.contains("FUSS-BEREICH-NUR-POSITIV"), "Bereich (Hide-Bedingung wahr) muss weg: " + footer);
        assertTrue(footer.startsWith("FUSS-RECHTS -5 X"), footer);
    }

    @Test
    @Tag("REQ-0010")
    void footerFormatsFollowLocaleRules() throws Exception {
        // Format ohne Sprachangabe -> Ersatzsprache gilt auch in der Fusszeile (REQ-0006)
        byte[] odt = render("{\"offer\":{\"reference\":\"X\"},\"numbertest\":500000,\"kunde\":{\"anrede\":\"FRAU\"}}", Locale.US);
        assertEquals("FUSS-ERSTE 500,000", headersAndFooters(odt).get("First_20_Page/footer"));
    }

    private byte[] render(String json, Locale defaultLocale) throws Exception {
        return RenderEngine.mergeTemplate(baseUri.resolve("header_footer.odt").toURL(), mapper.readTree(json), defaultLocale);
    }

    /** Text je Master-Page und Kopf-/Fusszeilen-Variante, z.B. "Standard/footer-left". */
    private static Map<String, String> headersAndFooters(byte[] odt) throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var dom = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(styles(odt))));
        var pages = dom.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:style:1.0", "master-page");
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < pages.getLength(); i++) {
            Element masterPage = (Element) pages.item(i);
            for (Node part = masterPage.getFirstChild(); part != null; part = part.getNextSibling()) {
                if (part instanceof Element e) {
                    result.put(masterPage.getAttributeNS("urn:oasis:names:tc:opendocument:xmlns:style:1.0", "name")
                            + "/" + e.getLocalName(), e.getTextContent().replaceAll("\\s+", " ").trim());
                }
            }
        }
        return result;
    }

    private static String styles(byte[] odt) throws Exception {
        try (var zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(odt))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("styles.xml")) {
                    return new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }
}
