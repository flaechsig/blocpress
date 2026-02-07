package org.blocpress.renderer.odt;

import lombok.SneakyThrows;
import org.blocpress.renderer.TemplateElement;
import org.odftoolkit.odfdom.pkg.OdfElement;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for working with OpenDocument Text (ODT) elements.
 */
public class OdtHelper {
    private OdtHelper() {
    }

    private static final Map<String, String> NS = Map.of(
            "office", "urn:oasis:names:tc:opendocument:xmlns:office:1.0",
            "text", "urn:oasis:names:tc:opendocument:xmlns:text:1.0",
            "table", "urn:oasis:names:tc:opendocument:xmlns:table:1.0",
            "draw", "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
    );

    /**
     * Extracts named elements into list of template elements
     */
    @SneakyThrows
    public static List<OdtTemplateElement> getNodes(OdfElement element, String name) {
        List<OdtTemplateElement> nodes = new ArrayList<>();
        String ns = null;
        NodeList nodeList = null;

        var idx = name.indexOf(':');
        if (idx > 0) {
            ns = NS.get(name.substring(0, idx));
        }
        if (ns != null) {
            nodeList = element.getElementsByTagNameNS(ns, name.substring(idx + 1));
        } else {
            nodeList = element.getElementsByTagName(name);
        }

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node n = nodeList.item(i);
            nodes.add(new OdtTemplateElement((OdfElement) n));
        }
        return nodes;
    }

}
