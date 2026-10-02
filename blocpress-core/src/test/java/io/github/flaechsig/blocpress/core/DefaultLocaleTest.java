package io.github.flaechsig.blocpress.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static io.github.flaechsig.blocpress.util.ResourceUtil.extractOdtContent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ersatzsprache fuer Number-Styles ohne eigene Sprachangabe.
 *
 * <p>{@code numberformats.odt}: die Felder 1-4 nutzen Styles <b>ohne</b> {@code number:language}
 * (ganzzahlig, 2 Nachkommastellen, gruppiert, gruppiert mit 2 Nachkommastellen), die Felder 5-9
 * Prozent-/Waehrungs-Styles mit ausdruecklich {@code number:language="de" number:country="DE"}.</p>
 */
public class DefaultLocaleTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI baseUri = Path.of(System.getProperty("user.dir"), "src/test/resources")
            .toAbsolutePath()
            .toUri();

    @Test
    @Tag("REQ-0006")
    public void styleWithoutLanguageUsesGermanDefault() throws Exception {
        assertEquals(List.of("500000", "500000,00", "500.000", "500.000,00"),
                render(500000, "de-DE").subList(0, 4));
        assertEquals("83,00", render(83, "de-DE").get(1));
    }

    @Test
    @Tag("REQ-0006")
    public void styleWithoutLanguageUsesEnglishDefault() throws Exception {
        assertEquals(List.of("500000", "500000.00", "500,000", "500,000.00"),
                render(500000, "en-US").subList(0, 4));
        assertEquals("83.00", render(83, "en-US").get(1));
    }

    @Test
    @Tag("REQ-0005")
    public void languageInStyleWinsOverDefault() throws Exception {
        List<String> german = render(-98765.4321, "de-DE");
        List<String> english = render(-98765.4321, "en-US");

        // Styles mit number:language="de" bleiben deutsch, egal welche Ersatzsprache gilt
        // (die Vorlage setzt vor %/€/EUR ein geschuetztes Leerzeichen U+00A0)
        assertEquals(List.of("-98765 %", "-98765,43 %", "-98.765 €", "-98.765,43 €",
                        "-98.765,43 EUR"),
                english.subList(4, 9));
        assertEquals(german.subList(4, 9), english.subList(4, 9));
    }

    @Test
    @Tag("REQ-0006")
    public void defaultLocaleIgnoresOperatingSystemLocale() throws Exception {
        Locale original = Locale.getDefault();
        try {
            // JVM/OS-Locale bewusst gegenlaeufig setzen: massgeblich ist nur die uebergebene Ersatzsprache
            Locale.setDefault(Locale.US);
            assertEquals("500.000", render(500000, "de-DE").get(2));
            Locale.setDefault(Locale.GERMANY);
            assertEquals("500,000", render(500000, "en-US").get(2));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void legacyApiKeepsGermanDefault() throws Exception {
        var node = mapper.readTree("{\"numbertest\": 500000}");
        var lines = extractOdtContent(RenderEngine.mergeTemplate(template(), node)).lines().toList();
        assertEquals("500.000", lines.get(2));
    }

    @Test
    public void localeSupportDetectsMissingLanguageData() {
        assertTrue(LocaleSupport.isAvailable(Locale.GERMANY));
        assertTrue(LocaleSupport.isAvailable(Locale.of("de")));
        assertFalse(LocaleSupport.isAvailable(Locale.forLanguageTag("xx-XX")));
        assertFalse(LocaleSupport.isAvailable(Locale.ROOT));
    }

    private List<String> render(double value, String defaultLocale) throws Exception {
        var node = mapper.readTree("{\"numbertest\": " + value + "}");
        byte[] odt = RenderEngine.mergeTemplate(template(), node, Locale.forLanguageTag(defaultLocale));
        return extractOdtContent(odt).lines().toList();
    }

    private URL template() throws Exception {
        return baseUri.resolve("numberformats.odt").normalize().toURL();
    }
}
