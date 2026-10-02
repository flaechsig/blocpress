package io.github.flaechsig.blocpress.render;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bedingte Bereiche ({@code text:section}) im fertigen PDF (REQ-0002).
 *
 * <p>Bis 2.6.0 fehlten im PDF <b>alle</b> bedingten Bereiche: blocpress entfernte beim Anzeigen
 * zwar die Bedingung, liess aber {@code text:display="condition"} stehen — LibreOffice blendet
 * einen solchen Bereich aus. {@code SectionTest} (core) sah das nicht, weil es nur content.xml
 * liest. Dieser Test prueft das gerenderte Ergebnis.</p>
 */
class SectionPdfTest {

    @ParameterizedTest
    @Tag("REQ-0002")
    @CsvSource(delimiter = '|', value = {
            "FRAU   | Absatz der unter der Bedingung „kunde.anrede“ == „FRAU“ angezeigt werden soll. Hier folgt dann weiterer Text und auch der Nachname von Müller.",
            "HERR   | Dieser Absatz wird für angezeigt, wenn die Anrede auf „HERR“ steht. Und dann kommt weiterer Text und auch der Nachname von Müller.",
            "DIVERS | Wenn weder Frau oder Herr im Attribut steht, dann wird für dieser Text angezeigt."
    })
    void onlyTheMatchingSectionIsVisibleInPdf(String anrede, String expected) throws Exception {
        RenderResource resource = new RenderResource();
        set(resource, "libreOfficePool", new LibreOfficePool());
        set(resource, "localeConfig", RenderLocaleConfig.of("de-DE"));

        File pdf;
        try (InputStream template = Files.newInputStream(Path.of("../blocpress-core/src/test/resources/section.odt"))) {
            pdf = resource.renderDocumentMultipart("application/pdf", template,
                    "{\"kunde\":{\"anrede\":\"" + anrede + "\",\"nachname\":\"Müller\"}}");
        }
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc).replace(' ', ' ').replaceAll("\\s+", " ").trim();
            assertEquals(expected, text);
        }
    }

    private static void set(Object target, String field, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
