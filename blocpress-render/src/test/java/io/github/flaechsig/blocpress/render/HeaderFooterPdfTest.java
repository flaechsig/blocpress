package io.github.flaechsig.blocpress.render;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kopf-/Fusszeilen im fertigen PDF (REQ-0010): {@code header_footer.odt} ergibt drei Seiten —
 * Seite 1 mit Master-Page "First Page", Seite 2 (links) mit {@code footer-left}, Seite 3 (rechts)
 * mit {@code footer}, bedingtem Text und Bereich. Geprueft wird je Seite.
 */
class HeaderFooterPdfTest {

    private static final String DATA = """
            {"offer": {"reference": "ANG-2026-0815", "supportId": "SUP-77"},
             "numbertest": 500000, "kunde": {"anrede": "FRAU"}}
            """;

    @Test
    @Tag("REQ-0010")
    void headersAndFootersAreFilledOnEveryPage() throws Exception {
        RenderResource resource = new RenderResource();
        set(resource, "libreOfficePool", new LibreOfficePool());
        set(resource, "localeConfig", RenderLocaleConfig.of("de-DE"));

        File pdf;
        try (InputStream template = Files.newInputStream(
                Path.of("../blocpress-core/src/test/resources/header_footer.odt"))) {
            pdf = resource.renderDocumentMultipart("application/pdf", template, DATA);
        }

        try (PDDocument doc = PDDocument.load(pdf)) {
            assertEquals(3, doc.getNumberOfPages());
            String page1 = page(doc, 1);
            String page2 = page(doc, 2);
            String page3 = page(doc, 3);

            assertTrue(page1.contains("KOPF-ERSTE ANG-2026-0815"), page1);
            assertTrue(page1.contains("RUMPF-REF ANG-2026-0815"), page1);
            assertTrue(page1.contains("FUSS-ERSTE 500.000"), page1);

            assertTrue(page2.contains("KOPF-STD ANG-2026-0815"), page2);
            assertTrue(page2.contains("FUSS-LINKS 500.000 ANG-2026-0815 SUP-77"), page2);

            assertTrue(page3.contains("FUSS-RECHTS 500.000 ANG-2026-0815"), page3);
            assertTrue(page3.contains("FUSS-ANREDE Frau"), page3);
            assertTrue(page3.contains("FUSS-BEREICH-NUR-POSITIV"), page3);

            String all = page1 + page2 + page3;
            assertFalse(all.contains("REF-BEISPIEL") || all.contains("SUP-BEISPIEL") || all.contains("-12.346"),
                    "Beispielwert der Vorlage im PDF: " + all);
        }
    }

    private static String page(PDDocument doc, int number) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(number);
        stripper.setEndPage(number);
        return stripper.getText(doc).replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    private static void set(Object target, String field, Object value) throws Exception {
        var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
