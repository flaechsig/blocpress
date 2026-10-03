package io.github.flaechsig.blocpress.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static io.github.flaechsig.blocpress.util.ResourceUtil.extractOdtContent;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-0001: Benutzerfelder ({@code text:user-field-get}) werden durch die Werte am JSON-Pfad ersetzt.
 * Geprueft wird, dass die Datenwerte erscheinen <b>und</b> die Beispielwerte der Vorlage verschwinden —
 * ein Vergleich gegen ein „erwartetes" Dokument mit denselben Beispielwerten beweist das nicht.
 */
class FieldReplacementTest {

    @Test
    @Tag("REQ-0001")
    void userFieldsAreReplacedByJsonValues() throws Exception {
        var data = new ObjectMapper().readTree("""
                {"invoice": {"number": "LT-2026-0042", "currency": "€", "paymentTermsDays": 30},
                 "customer": {"firstname": "Erika", "lastname": "Mustermann", "street": "Hauptstr. 5",
                              "postcode": "50667", "city": "Köln"},
                 "positions": [{"name": "Render-Lizenz", "description": "Jahreslizenz", "quantity": 12,
                                "unitPrice": 1234.5, "total": 14814.0}],
                 "summary": {"netTotal": 14814.0, "taxRate": 19, "taxAmount": 2814.66, "grossTotal": 17628.66}}
                """);
        String text = extractOdtContent(RenderEngine.mergeTemplate(
                Path.of("../docs/samples/quickstart/invoice.odt").toUri().toURL(), data));

        for (String value : new String[]{"Erika", "Mustermann", "Hauptstr. 5", "50667", "Köln", "LT-2026-0042",
                "Render-Lizenz", "Jahreslizenz", "1234,50"}) {
            assertTrue(text.contains(value), "fehlt: '" + value + "' — " + text);
        }
        for (String example : new String[]{"Firstname", "Lastname", "Example Street 1", "Sample Citiy",
                "BP-Number", "Service Name"}) {
            assertFalse(text.contains(example), "Beispielwert nicht ersetzt: '" + example + "' — " + text);
        }
    }
}
