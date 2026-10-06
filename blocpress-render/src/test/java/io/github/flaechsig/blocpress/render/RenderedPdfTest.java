package io.github.flaechsig.blocpress.render;

import org.junit.jupiter.api.DisplayName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nachweise auf Ebene des fertigen PDFs (REQ-0001/0002/0003/0006/0025).
 *
 * <p>Die core-Tests pruefen content.xml. Dass ein Inhalt dort steht, heisst nicht, dass er im
 * PDF ankommt — so fehlten bis 2.6.0 alle bedingten Bereiche im PDF, obwohl die core-Tests gruen
 * waren. Diese Tests rendern ueber den echten Pfad (merge + LibreOffice) und lesen das PDF.</p>
 */
class RenderedPdfTest {

    private static final Path CORE_RESOURCES = Path.of("../blocpress-core/src/test/resources");

    /** Rechnungsvorlage mit Benutzerfeldern (text:user-field-get), Daten wie im Lasttest. */
    static final String INVOICE_DATA = """
            {"invoice": {"number": "LT-2026-0042", "date": "2026-10-02", "currency": "€", "paymentTermsDays": 30}, "customer": {"firstname": "Erika", "lastname": "Mustermann", "street": "Hauptstr. 5", "postcode": "50667", "city": "Köln"}, "positions": [{"name": "Render-Lizenz", "description": "Jahreslizenz", "quantity": 12, "unitPrice": 1234.5, "total": 14814.0}, {"name": "Wartung", "description": "Supportvertrag", "quantity": 3, "unitPrice": 999.99, "total": 2999.97}, {"name": "Schulung", "description": "Workshop", "quantity": 1, "unitPrice": 450.0, "total": 450.0}], "summary": {"netTotal": 18263.97, "taxRate": 19, "taxAmount": 3470.15, "grossTotal": 21734.12}}
            """;

    @Test
    @DisplayName("REQ-0001: fieldsAreReplacedInPdf")
    void fieldsAreReplacedInPdf() throws Exception {
        String pdf = render(Path.of("../site/samples/quickstart/invoice.odt"), INVOICE_DATA);

        for (String value : new String[]{"Erika Mustermann", "Hauptstr. 5", "50667 Köln", "LT-2026-0042",
                "Payment Terms: 30 Days", "Render-Lizenz", "1234,50 €", "Total Amount Due: 21734,12 €"}) {
            assertTrue(pdf.contains(value), "fehlt im PDF: '" + value + "' — " + pdf);
        }
        // Beispielwerte aus den Deklarationen der Vorlage duerfen nicht mehr erscheinen
        for (String example : new String[]{"Firstname", "Lastname", "Example Street 1", "Sample Citiy",
                "BP-Number", "Service Name"}) {
            assertFalse(pdf.contains(example), "Beispielwert der Vorlage im PDF: '" + example + "' — " + pdf);
        }
    }

    @Test
    @DisplayName("REQ-0003: loopRowsAppearOncePerElementInOrder")
    void loopRowsAppearOncePerElementInOrder() throws Exception {
        String pdf = render("loop_table.odt", """
                {"kunde": "Max Mustermann", "produkte": [
                  {"name": "Apfel",  "menge": 1, "preis": 1.00},
                  {"name": "Birne",  "menge": 2, "preis": 1.50},
                  {"name": "Banane", "menge": 3, "preis": 0.50},
                  {"name": "Kiwi",   "menge": 4, "preis": 2.25}
                ]}
                """);

        int apfel = pdf.indexOf("Apfel");
        int birne = pdf.indexOf("Birne");
        int banane = pdf.indexOf("Banane");
        int kiwi = pdf.indexOf("Kiwi");
        assertTrue(apfel >= 0 && apfel < birne && birne < banane && banane < kiwi,
                "Zeilen fehlen oder falsche Reihenfolge: " + pdf);
        for (String name : new String[]{"Apfel", "Birne", "Banane", "Kiwi"}) {
            assertEquals(pdf.indexOf(name), pdf.lastIndexOf(name), "Zeile mehrfach: " + name + " — " + pdf);
        }
        for (String price : new String[]{"1,00", "1,50", "0,50", "2,25"}) {
            assertTrue(pdf.contains(price), "Preis fehlt: " + price + " — " + pdf);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @DisplayName("REQ-0002: conditionalTextShowsMatchingBranchInPdf")
    @CsvSource({
            "FRAU, Liebe Frau Müller,  Lieber Herr",
            "HERR, Lieber Herr Müller, Liebe Frau"
    })
    void conditionalTextShowsMatchingBranchInPdf(String anrede, String expected, String forbidden) throws Exception {
        String pdf = render("IfCondition.odt",
                "{\"kunde\":{\"anrede\":\"" + anrede + "\",\"nachname\":\"Müller\"}}");
        assertEquals(expected, pdf);
        assertFalse(pdf.contains(forbidden), pdf);
    }

    @Test
    @DisplayName("REQ-0006: dateAndDateTimeFormatsInPdf")
    void dateAndDateTimeFormatsInPdf() throws Exception {
        String pdf = render("dateformats.odt", "{\"datumtest\":\"2026-10-03T14:30:00\"}");
        for (String expected : new String[]{"DE 03.10.2026", "ISO 2026-10-03", "KURZ 3.10.26", "ZEIT 03.10.2026 14:30",
                "OHNE 03/10/2026"}) {
            assertTrue(pdf.contains(expected), "fehlt im PDF: '" + expected + "' — " + pdf);
        }
    }

    @Test
    @DisplayName("REQ-0025: sameInputGivesSameTextAtSamePositionsInPdf")
    void sameInputGivesSameTextAtSamePositionsInPdf() throws Exception {
        Path invoice = Path.of("../site/samples/quickstart/invoice.odt");
        String first = renderWithPositions(invoice, INVOICE_DATA);
        String second = renderWithPositions(invoice, INVOICE_DATA);

        assertTrue(first.contains("Erika"), first);
        assertEquals(first, second);
    }

    /**
     * Rendert zu PDF und liefert jeden Textabschnitt mit Seite und Position. Verglichen wird so
     * der Inhalt, nicht die Bytes: Das PDF traegt ein Erzeugungsdatum, das sich bei jedem Lauf aendert.
     */
    static String renderWithPositions(Path template, String json) throws Exception {
        File pdf = renderToFile(template, json);
        var out = new StringBuilder();
        try (PDDocument doc = PDDocument.load(pdf)) {
            new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    TextPosition start = positions.get(0);
                    out.append(getCurrentPageNo()).append(' ')
                            .append(String.format(Locale.ROOT, "%.2f %.2f", start.getXDirAdj(), start.getYDirAdj()))
                            .append(' ').append(text).append('\n');
                }
            }.getText(doc);
        }
        return out.toString();
    }

    private static File renderToFile(Path template, String json) throws Exception {
        RenderResource resource = new RenderResource();
        set(resource, "libreOfficePool", new LibreOfficePool());
        set(resource, "localeConfig", RenderLocaleConfig.of("de-DE"));
        try (InputStream in = Files.newInputStream(template)) {
            return resource.renderDocumentMultipart("application/pdf", in, json);
        }
    }

    /** Rendert eine Vorlage aus den core-Testressourcen zu PDF und liefert den Text (Whitespace normalisiert). */
    static String render(String template, String json) throws Exception {
        return render(CORE_RESOURCES.resolve(template), json);
    }

    static String render(Path template, String json) throws Exception {
        File pdf = renderToFile(template, json);
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc).replace(' ', ' ').replaceAll("\\s+", " ").trim();
        }
    }

    private static void set(Object target, String field, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
