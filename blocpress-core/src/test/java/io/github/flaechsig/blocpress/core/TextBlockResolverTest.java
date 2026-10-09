package io.github.flaechsig.blocpress.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static io.github.flaechsig.blocpress.util.ResourceUtil.extractOdtContent;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regeln fuer verknuepfte Abschnitte (ADR-0018). {@code sample-05-baustein.odt} verknuepft den Baustein
 * {@code Sondervereinbarung} unter einem Host, der nicht aufloesbar ist ({@code unreachable.invalid}):
 * wuerde die Regel die Verknuepfung oeffnen, schluege das Rendern fehl.
 */
class TextBlockResolverTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final URI BASE = Path.of(System.getProperty("user.dir"), "src/test/resources").toAbsolutePath().toUri();
    private static final String JSON = """
            {"customer": [{"firstName": "Michael", "lastName": "Miller",
              "address": {"street": "Example Street 1", "postcode": "12345", "city": "Hamburg"}}]}
            """;

    @ParameterizedTest
    @CsvSource({
            "http://host:8080/api/webdav/released/bausteine/Bankverbindung.odt, Bankverbindung",
            "https://studio.example/api/webdav/design/bausteine/Adresse%20Kunde.odt, Adresse Kunde",
            "http://host/api/webdav/bausteine/A+B.odt?x=1#y, A+B",
            "../bausteine/Lokal.odt, Lokal",
            "file:///srv/bausteine/Datei.odt, Datei"
    })
    @DisplayName("nameOf liest den Namen aus .../bausteine/{name}.odt")
    void nameOfReadsName(String href, String expected) {
        assertEquals(Optional.of(expected), TextBlocks.nameOf(href));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "../special_agreement.odt", "http://host/api/webdav/released/templates/X.odt",
            "http://host/bausteine/", "http://host/bausteine/a%2Fb.odt", "http://host/bausteine/X.ott",
            "http://host/bausteine/%zz.odt"
    })
    @DisplayName("nameOf lehnt Pfade ausserhalb von /bausteine/{name}.odt ab")
    void nameOfRejectsOtherPaths(String href) {
        assertTrue(TextBlocks.nameOf(href).isEmpty(), href);
        assertTrue(TextBlocks.nameOf(null).isEmpty());
    }

    @Test
    @DisplayName("byName setzt den Baustein aus der Suche ein, ohne die Verknuepfung zu oeffnen")
    void byNameUsesLookupInsteadOfLink() throws Exception {
        byte[] baustein = Files.readAllBytes(Path.of(BASE.resolve("special_agreement.odt")));
        List<String> asked = new ArrayList<>();

        byte[] odt = RenderEngine.mergeTemplate(url("sample-05-baustein.odt"), json(), Locale.GERMANY,
                TextBlockResolver.byName(name -> {
                    asked.add(name);
                    return baustein;
                }));

        assertEquals(List.of("Sondervereinbarung"), asked);
        String text = extractOdtContent(odt);
        assertTrue(text.contains("Special Agreement with Michael Miller"), text);
    }

    @Test
    @DisplayName("byName lehnt unbekannte Bausteine und fremde Verknuepfungen ab")
    void byNameRejects() {
        var unknown = assertThrows(TextBlockRejectedException.class, () -> RenderEngine.mergeTemplate(
                url("sample-05-baustein.odt"), json(), Locale.GERMANY, TextBlockResolver.byName(name -> null)));
        assertEquals("Building block not available: Sondervereinbarung", unknown.getMessage());

        var foreign = assertThrows(TextBlockRejectedException.class, () -> RenderEngine.mergeTemplate(
                url("sample-05.odt"), json(), Locale.GERMANY, TextBlockResolver.byName(name -> new byte[0])));
        assertFalse(foreign.getMessage().contains("special_agreement"), foreign.getMessage());
    }

    @Test
    @DisplayName("rejectAll lehnt jede Verknuepfung ab, laesst Vorlagen ohne Verknuepfung durch")
    void rejectAll() throws Exception {
        assertThrows(TextBlockRejectedException.class, () -> RenderEngine.mergeTemplate(
                url("sample-05-baustein.odt"), json(), Locale.GERMANY, TextBlockResolver.rejectAll()));
        assertNotNull(RenderEngine.mergeTemplate(url("section.odt"), MAPPER.readTree("{}"), Locale.GERMANY,
                TextBlockResolver.rejectAll()));
    }

    @Test
    @DisplayName("expandTextBlocks setzt Bausteine ohne Daten ein und entfernt die Verknuepfung")
    void expandTextBlocksWithoutData() throws Exception {
        byte[] baustein = Files.readAllBytes(Path.of(BASE.resolve("special_agreement.odt")));

        byte[] odt = RenderEngine.expandTextBlocks(url("sample-05-baustein.odt"), TextBlockResolver.byName(n -> baustein));

        String content = contentXml(odt);
        assertFalse(content.contains("text:section-source"), "Verknuepfung muss entfernt sein");
        assertTrue(extractOdtContent(odt).contains("Special Agreement with"));
    }

    private static URL url(String name) throws Exception {
        return BASE.resolve(name).normalize().toURL();
    }

    private static JsonNode json() throws Exception {
        return MAPPER.readTree(JSON);
    }

    private static String contentXml(byte[] odt) throws Exception {
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(odt))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (e.getName().equals("content.xml")) {
                    return new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("content.xml fehlt");
    }
}
