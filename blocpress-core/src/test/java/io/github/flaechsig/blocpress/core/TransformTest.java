package io.github.flaechsig.blocpress.core;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static io.github.flaechsig.blocpress.util.ResourceUtil.extractPdfContent;
import static io.github.flaechsig.blocpress.util.ResourceUtil.loadDocumentAsBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Konvertierung eines gemergten ODT nach PDF/RTF ueber headless LibreOffice (REQ-0012).
 *
 * <p>Benoetigt {@code soffice} auf dem PATH; ohne LibreOffice werden die Tests
 * uebersprungen (Unit-Tests bleiben ohne LibreOffice lauffaehig). Das req-check-Gate
 * wertet einen uebersprungenen Test bewusst nicht als Nachweis.</p>
 */
@Tag("REQ-0012")
@EnabledIf("sofficeAvailable")
public class TransformTest {

    static boolean sofficeAvailable() {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (new File(dir, "soffice").canExecute()) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void transformToPdf() throws Exception {
        byte[] odtBytes = loadDocumentAsBytes("/kuendigung_generated.odt");
        String expected = extractPdfContent(loadDocumentAsBytes("/kuendigung_generated.pdf"));

        byte[] pdf = LibreOfficeProcessor.refreshAndTransform(odtBytes, OutputFormat.PDF);

        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII));
        assertEquals(words(expected), words(extractPdfContent(pdf)));
    }

    /**
     * Vergleicht Inhalt und Wortfolge, nicht den Zeilenumbruch: der Umbruch im PDF haengt
     * von den installierten Schriften ab und unterscheidet sich z.B. zwischen lokalem
     * Rechner und CI-Runner.
     */
    private static String words(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    @Test
    public void transformToRtf() throws Exception {
        byte[] odtBytes = loadDocumentAsBytes("/kuendigung_generated.odt");

        String rtf = new String(LibreOfficeProcessor.refreshAndTransform(odtBytes, OutputFormat.RTF),
                StandardCharsets.ISO_8859_1);

        assertTrue(rtf.startsWith("{\\rtf1"), "Ausgabe ist kein RTF");
        assertTrue(rtf.contains("Sehr geehrte Damen und Herren"), "Anrede fehlt im RTF");
        assertTrue(rtf.contains("0174/123456789"), "Vertragsnummer fehlt im RTF");
        assertTrue(rtf.contains("Lastschriftverfahren"), "Brieftext fehlt im RTF");
    }

}
