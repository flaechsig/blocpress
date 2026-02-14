package io.github.flaechsig.blocpress.render;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.odftoolkit.odfdom.doc.OdfTextDocument;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.rtf.RTFEditorKit;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.fail;

public final class TestDocumentUtil {

    private TestDocumentUtil() {}

    public static String extractPdfText(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            var stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    public static String extractRtfText(byte[] rtf) throws Exception {
        var kit = new RTFEditorKit();
        var doc = new DefaultStyledDocument();
        try (InputStream in = new ByteArrayInputStream(rtf)) {
            kit.read(in, doc, 0);
        }
        return doc.getText(0, doc.getLength());
    }

    public static String extractOdtText(byte[] odt) throws Exception {
        var doc = OdfTextDocument.loadDocument(new ByteArrayInputStream(odt));
        return doc.getContentRoot().getTextContent();
    }


    public static String readZipEntry(byte[] zipBytes, String entryName) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (entryName.equals(e.getName())) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        fail("ZIP entry not found: " + entryName);
        return null; // unreachable
    }

    public static String normalizeText(String s) {
        return s.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public static String normalizeXml(String xml) {
        return xml.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll(">\\s+<", "><")
                .trim();
    }
}