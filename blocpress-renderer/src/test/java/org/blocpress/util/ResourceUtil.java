package org.blocpress.util;

import org.apache.commons.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.odftoolkit.odfdom.doc.OdfTextDocument;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.Base64;

public class ResourceUtil {

    public static byte[] loadDocumentAsBytes(String name) {
        try {
            return IOUtils.toByteArray(ResourceUtil.class.getResourceAsStream(name));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Extrahiert den reinen Textinhalt aus einem ODT-Dokument (ohne Metadaten/Formatierungen).
     */
    public static String extractOdtContent(byte[] odtBytes) throws Exception {
        try (ByteArrayInputStream in = new ByteArrayInputStream(odtBytes)) {
            OdfTextDocument document = OdfTextDocument.loadDocument(in);
            StringBuilder text = new StringBuilder();

            var paragraphs = document.getContentRoot().getElementsByTagName("text:p");

            for (int i = 0; i < paragraphs.getLength(); i++) {
                Node p = paragraphs.item(i);
                text.append(collectTextRecursively(p)).append("\n");
            }

            return text.toString().trim();
        }
    }

    private static String collectTextRecursively(Node node) {
        StringBuilder sb = new StringBuilder();
        collect(node, sb);
        return sb.toString();
    }

    private static void collect(Node node, StringBuilder sb) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            switch (child.getNodeType()) {
                case Node.TEXT_NODE -> {
                    sb.append(child.getTextContent());
                }
                case Node.ELEMENT_NODE -> {
                    Element el = (Element) child;
                    String ns = el.getNamespaceURI();
                    String local = el.getLocalName();

                    // ODF text:s -> space(s). Namespace für text ist "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
                    if ("s".equals(local) && "urn:oasis:names:tc:opendocument:xmlns:text:1.0".equals(ns)) {
                        // Anzahl der zu erzeugenden Leerzeichen (text:c), default 1
                        String countAttr = el.getAttribute("text:c");
                        int count = 1;
                        if (countAttr != null && !countAttr.isBlank()) {
                            try {
                                count = Integer.parseInt(countAttr);
                                if (count < 1) count = 1;
                            } catch (NumberFormatException ignored) {
                                count = 1;
                            }
                        }

                        // Prüfe links: endet bereits der StringBuilder mit Whitespace?
                        boolean leftHasWs = !sb.isEmpty() && Character.isWhitespace(sb.charAt(sb.length() - 1));

                        // Prüfe rechts: ist das nächste text-Node (falls vorhanden) mit Whitespace beginnend?
                        boolean rightHasWs = false;
                        Node next = child.getNextSibling();
                        // Scanne bis zum nächsten Text-Node oder Element-Node mit Text
                        while (next != null) {
                            if (next.getNodeType() == Node.TEXT_NODE) {
                                String nxt = next.getTextContent();
                                rightHasWs = nxt != null && !nxt.isEmpty() && Character.isWhitespace(nxt.charAt(0));
                                break;
                            }
                            if (next.getNodeType() == Node.ELEMENT_NODE) {
                                // Falls das nächste Element selbst Text-Kinder besitzt, prüfe dessen ersten Text-Child
                                Node firstGrand = next.getFirstChild();
                                while (firstGrand != null) {
                                    if (firstGrand.getNodeType() == Node.TEXT_NODE) {
                                        String nxt = firstGrand.getTextContent();
                                        rightHasWs = nxt != null && !nxt.isEmpty() && Character.isWhitespace(nxt.charAt(0));
                                        break;
                                    }
                                    firstGrand = firstGrand.getNextSibling();
                                }
                                break;
                            }
                            next = next.getNextSibling();
                        }

                        // Wenn links oder rechts bereits Whitespace existiert, füge kein zusätzliches Space hinzu.
                        if (leftHasWs || rightHasWs) {
                            // eventuell nichts tun, oder bei count>1 noch zusätzliche notwendige Spaces ergänzen
                            if (!leftHasWs && count > 0) {
                                // falls links kein WS aber rechts schon, füge (count-1) zusätzliche, weil rechts 1 schon da ist
                                for (int k = 0; k < count - 1; k++) sb.append(' ');
                            } else if (!rightHasWs && leftHasWs && count > 1) {
                                // links schon 1 WS vorhanden, wenn count>1 füge (count-1) zusätzliche
                                for (int k = 0; k < count - 1; k++) sb.append(' ');
                            }
                            // sonst nichts hinzufügen
                        } else {
                            // weder links noch rechts Whitespace -> füge genau count Leerzeichen ein
                            for (int k = 0; k < count; k++) sb.append(' ');
                        }
                    } else {
                        // Rekursion für andere Elemente (z.B. text:span)
                        collect(child, sb);
                    }
                }
                default -> {
                    // ignoriere sonstige Knotentypen
                }
            }
        }
    }

    public static String extractPdfContent(byte[] entity) throws IOException {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(entity))) {
            return new PDFTextStripper().getText(document);
        }
    }

    public static String extractRtfContent(byte[] rtfBytes) throws Exception {
        try (LineNumberReader reader = new LineNumberReader(
                new InputStreamReader(new ByteArrayInputStream(rtfBytes)))) {

            StringBuilder text = new StringBuilder();
            String line;
            boolean inTextBlock = false;

            while ((line = reader.readLine()) != null) {
                // RTF-Steuerwörter beginnen mit '\', Text ist alles andere
                if (line.contains("\\")) {
                    // Extrahiere Text nach dem letzten '\'
                    String[] parts = line.split("\\\\");
                    if (parts.length > 1) {
                        String lastPart = parts[parts.length - 1];
                        if (!lastPart.trim().isEmpty() && !lastPart.startsWith("}")) {
                            text.append(lastPart).append(" ");
                        }
                    }
                } else if (!line.trim().isEmpty() && line.contains("}")) {
                    // Textzeilen ohne Steuerwörter (z. B. reine Textblöcke)
                    String content = line.replaceAll("[{}]", "").trim();
                    if (!content.isEmpty()) {
                        text.append(content).append(" ");
                    }
                }
            }
            return text.toString().trim();
        }
    }
}
