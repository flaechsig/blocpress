package io.github.flaechsig.blocpress.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * REQ-0025: Dieselbe Vorlage mit denselben Daten ergibt bei jedem Lauf dasselbe Dokument.
 * Verglichen wird jeder Eintrag der erzeugten ODT, nicht nur der Text. Ausgenommen ist
 * {@code meta.xml}: Sie traegt das Aenderungsdatum, das sich bei jedem Lauf aendert.
 */
class DeterministicRenderTest {

    @ParameterizedTest(name = "{displayName} [{index}] {0}")
    @DisplayName("REQ-0025: sameInputGivesIdenticalOdt")
    @CsvSource(delimiter = '|', value = {
            "../site/samples/quickstart/invoice.odt|{\"invoice\": {\"number\": \"LT-2026-0042\", \"date\": \"2026-10-02\", \"currency\": \"€\", \"paymentTermsDays\": 30}, \"customer\": {\"firstname\": \"Erika\", \"lastname\": \"Mustermann\", \"street\": \"Hauptstr. 5\", \"postcode\": \"50667\", \"city\": \"Köln\"}, \"positions\": [{\"name\": \"Render-Lizenz\", \"description\": \"Jahreslizenz\", \"quantity\": 12, \"unitPrice\": 1234.5, \"total\": 14814.0}, {\"name\": \"Wartung\", \"description\": \"Supportvertrag\", \"quantity\": 3, \"unitPrice\": 999.99, \"total\": 2999.97}], \"summary\": {\"netTotal\": 17813.97, \"taxRate\": 19, \"taxAmount\": 3384.65, \"grossTotal\": 21198.62}}",
            "src/test/resources/section.odt|{\"kunde\": {\"anrede\": \"FRAU\", \"nachname\": \"Müller\"}}",
            "src/test/resources/header_footer.odt|{\"offer\": {\"reference\": \"A-17\"}, \"customer\": {\"name\": \"Erika Mustermann\"}}",
            "src/test/resources/dateformats.odt|{\"datumtest\": \"2026-10-03T14:30:00\"}"
    })
    void sameInputGivesIdenticalOdt(String template, String json) throws Exception {
        JsonNode data = new ObjectMapper().readTree(json);
        var url = Path.of(template).toUri().toURL();

        Map<String, byte[]> first = entries(RenderEngine.mergeTemplate(url, data, Locale.GERMANY));
        Map<String, byte[]> second = entries(RenderEngine.mergeTemplate(url, data, Locale.GERMANY));

        assertEquals(first.keySet(), second.keySet());
        for (String name : first.keySet()) {
            assertArrayEquals(first.get(name), second.get(name), "Eintrag unterscheidet sich: " + name);
        }
    }

    private static Map<String, byte[]> entries(byte[] odt) throws Exception {
        Map<String, byte[]> entries = new TreeMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(odt))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (e.getName().equals("meta.xml")) continue;
                entries.put(e.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }
}
