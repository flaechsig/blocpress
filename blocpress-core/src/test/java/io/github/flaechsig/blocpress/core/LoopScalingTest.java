package io.github.flaechsig.blocpress.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.util.Locale;

import static io.github.flaechsig.blocpress.util.ResourceUtil.extractOdtContent;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-0097: Eine Tabellen-Schleife waechst linear mit der Zahl der Zeilen. Gemessen wird das
 * Verhaeltnis der Mischzeiten fuer 2000 und 500 Zeilen (linear: 4, quadratisch: 16), nicht eine
 * feste Zeit, damit der Test auf jeder Maschine gilt. Die Rechnung hat Waehrungs- und Zahlenformate
 * in jeder Zeile.
 */
class LoopScalingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("REQ-0097: mergingFourTimesTheRowsTakesAtMostEightTimesAsLong")
    void mergingFourTimesTheRowsTakesAtMostEightTimesAsLong() throws Exception {
        URL template = Path.of("../site/samples/quickstart/invoice.odt").toUri().toURL();
        ObjectNode small = invoice(500);
        ObjectNode large = invoice(2000);

        merge(template, small); // Aufwaermen (JIT, Klassen laden)
        long smallNanos = fastest(template, small);
        long largeNanos = fastest(template, large);

        double ratio = (double) largeNanos / smallNanos;
        assertTrue(ratio <= 8.0, "2000 Zeilen brauchen das %.1f-fache von 500 Zeilen (%d ms / %d ms)"
                .formatted(ratio, largeNanos / 1_000_000, smallNanos / 1_000_000));
        // die Zeilen sind wirklich da und formatiert
        String text = extractOdtContent(merge(template, large));
        assertTrue(text.contains("Wartung 1999"), "letzte Zeile fehlt");
        assertTrue(text.contains("1234,50 €"), "Betrag nicht im Format der Vorlage");
    }

    private static long fastest(URL template, ObjectNode data) {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            long start = System.nanoTime();
            merge(template, data);
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }

    private static byte[] merge(URL template, ObjectNode data) {
        return RenderEngine.mergeTemplate(template, data, Locale.GERMANY);
    }

    private static ObjectNode invoice(int rows) {
        ObjectNode data = MAPPER.createObjectNode();
        data.putObject("invoice").put("number", "LT-2026-0042").put("date", "2026-10-02")
                .put("currency", "€").put("paymentTermsDays", 30);
        data.putObject("customer").put("firstname", "Erika").put("lastname", "Mustermann")
                .put("street", "Hauptstr. 5").put("postcode", "50667").put("city", "Köln");
        String[][] base = {{"Render-Lizenz", "Jahreslizenz", "12", "1234.5", "14814.0"},
                {"Wartung", "Supportvertrag", "3", "999.99", "2999.97"},
                {"Schulung", "Workshop", "1", "450.0", "450.0"}};
        ArrayNode positions = data.putArray("positions");
        for (int i = 0; i < rows; i++) {
            String[] b = base[i % 3];
            positions.addObject().put("name", b[0] + " " + i).put("description", b[1])
                    .put("quantity", Integer.parseInt(b[2])).put("unitPrice", Double.parseDouble(b[3]))
                    .put("total", Double.parseDouble(b[4]));
        }
        data.putObject("summary").put("netTotal", 18263.97).put("taxRate", 19)
                .put("taxAmount", 3470.15).put("grossTotal", 21734.12);
        return data;
    }
}
