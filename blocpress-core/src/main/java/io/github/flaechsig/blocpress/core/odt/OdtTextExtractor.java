package io.github.flaechsig.blocpress.core.odt;

import org.odftoolkit.odfdom.doc.OdfTextDocument;
import org.odftoolkit.odfdom.dom.OdfDocumentNamespace;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Extracts plain text content from ODT binary data.
 *
 * <p>Traverses all {@code text:p} elements in the ODT content DOM and concatenates
 * their text content. Used by blocpress-workbench to populate the Elasticsearch index
 * field {@code extractedText} for full-text search (UC-19 / TI-7).</p>
 *
 * <p>No size limit is applied — Elasticsearch text fields have no practical upper bound.</p>
 */
public class OdtTextExtractor {

    /**
     * Extracts all paragraph text from the given ODT bytes.
     *
     * @param odtContent raw ODT file bytes
     * @return concatenated plain text of all text:p elements, trimmed
     * @throws IOException if the ODT cannot be parsed
     */
    public String extract(byte[] odtContent) throws IOException {
        try {
            OdfTextDocument doc = OdfTextDocument.loadDocument(
                    new ByteArrayInputStream(odtContent));
            NodeList paragraphs = doc.getContentDom()
                    .getElementsByTagNameNS(
                            OdfDocumentNamespace.TEXT.getUri(), "p");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < paragraphs.getLength(); i++) {
                String text = paragraphs.item(i).getTextContent();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append(' ');
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            throw new IOException("Failed to extract text from ODT", e);
        }
    }
}