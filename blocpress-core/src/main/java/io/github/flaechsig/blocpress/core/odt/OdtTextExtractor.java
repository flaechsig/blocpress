package io.github.flaechsig.blocpress.core.odt;

import org.odftoolkit.odfdom.doc.OdfTextDocument;
import org.odftoolkit.odfdom.dom.OdfDocumentNamespace;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Extracts plain text content from ODT binary data.
 *
 * <p>Collects the text of all paragraphs ({@code text:p}) and headings ({@code text:h}) of the
 * document body ({@code content.xml}) and of headers and footers ({@code styles.xml}), in
 * document order. Used by blocpress-workbench to populate the Elasticsearch index field
 * {@code extractedText} for full-text search (UC-19 / TI-7, US-0043).</p>
 *
 * <p>No size limit is applied — Elasticsearch text fields have no practical upper bound.</p>
 */
public class OdtTextExtractor {

    /**
     * Extracts all paragraph and heading text from the given ODT bytes, body first, then
     * headers and footers.
     *
     * @param odtContent raw ODT file bytes
     * @return concatenated plain text, trimmed
     * @throws IOException if the ODT cannot be parsed
     */
    public String extract(byte[] odtContent) throws IOException {
        try {
            OdfTextDocument doc = OdfTextDocument.loadDocument(
                    new ByteArrayInputStream(odtContent));
            StringBuilder sb = new StringBuilder();
            appendText(doc.getContentDom(), sb);
            appendText(doc.getStylesDom(), sb);
            return sb.toString().trim();
        } catch (Exception e) {
            throw new IOException("Failed to extract text from ODT", e);
        }
    }

    /** Hängt den Text aller text:p und text:h des Dokuments in Dokumentreihenfolge an. */
    private static void appendText(Document dom, StringBuilder sb) {
        NodeList elements = dom.getElementsByTagNameNS(OdfDocumentNamespace.TEXT.getUri(), "*");
        for (int i = 0; i < elements.getLength(); i++) {
            Node node = elements.item(i);
            String name = node.getLocalName();
            if ("p".equals(name) || "h".equals(name)) {
                String text = node.getTextContent();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append(' ');
                }
            }
        }
    }
}
