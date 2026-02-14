package io.github.flaechsig.blocpress.renderer.odt;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import io.github.flaechsig.blocpress.renderer.TemplateElement;
import org.odftoolkit.odfdom.dom.OdfContentDom;
import org.odftoolkit.odfdom.dom.element.text.TextSpanElement;
import org.odftoolkit.odfdom.pkg.OdfElement;
import org.w3c.dom.Node;

import java.util.*;
import java.util.regex.Pattern;

/**
 * ODT-Implementierung von {@link TemplateElement}. Kapselt ein {@link OdfElement}
 * und stellt Methoden zur Condition-Evaluierung und User-Field-Sammlung bereit.
 *
 * <p><b>Design-Referenzen:</b></p>
 * <ul>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-tf-5">TF-5: Dokument generieren</a> (Condition-Evaluierung, Field-Sammlung)</li>
 * </ul>
 */
public class OdtTemplateElement implements TemplateElement {
    private static final String TABLE_NS = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
    private static final String TEXT_NS  = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";

    OdfElement element;

    public OdtTemplateElement(OdfElement element) {
        this.element = element;
    }

    @Override
    public String getName() {
        return element.getAttribute("text:name");
    }

    @Override
    public void setName(String name) {
        element.setAttribute("text:name", name);
    }

    /**
     * Collects user fields from element nodes
     */
    @Override
    public List<TemplateElement> collectUserFields() {
        List<TemplateElement> fields = new ArrayList<>();

        OdtHelper.getNodes(element, "text:user-field-get").stream()
                .forEach(n -> fields.add(n));
        OdtHelper.getNodes(element, "text:variable-get").stream()
                .forEach(n -> fields.add(n));

        return fields;
    }

    /**
     * Checks if the TemplateElement matches the condition for provided data
     *
     * @return true if no condition is set or the condition matches for the provided data
     */
    @Override
    public boolean matchCondition(@NonNull JsonNode data) {
        if (!isConditional()) {
            return true;
        }

        var condition = element.getAttribute("text:condition");
        String updatedCondition = condition;

        if (!data.isMissingNode() && !data.isNull()) {
            List<PathValue> pathValues = new ArrayList<>();
            collectJsonPaths(data, "", pathValues);

            // Nach Schlüssellänge absteigend sortieren, um Teil-Matches zu vermeiden (a.b vs a.b.c)
            pathValues.sort((a, b) -> Integer.compare(b.path.length(), a.path.length()));

            for (PathValue pv : pathValues) {
                if (StringUtils.isBlank(pv.path)) {
                    continue;
                }
                Pattern p = Pattern.compile("(?<![\\w.])(?:ooow:)?" + Pattern.quote(pv.path) + "(?![\\w.])");
                String replacement = toOdfConditionLiteral(pv.value);
                updatedCondition = p.matcher(updatedCondition).replaceAll(replacement);
            }
        }
        return JexlConditionEvaluator.evaluate(updatedCondition);
    }

    public void resolveCondition(JsonNode data) {
        var tagName = element.getTagName();
        if (matchCondition(data)) {
            if ("text:conditional-text".equals(tagName)) {
                var trueValue = element.getAttribute("text:string-value-if-true");
                replaceWithSpan(trueValue);
            } else if ("text:section".equals(tagName)) {
                // text:section has a hide logic -> true means remove
                element.getParentNode().removeChild(element);
                element = null;
            }
        } else {
            if ("text:conditional-text".equals(tagName)) {
                var trueValue = element.getAttribute("text:string-value-if-false");
                replaceWithSpan(trueValue);
            } else if ("text:section".equals(tagName)) {
                // text:section has a hide logic -> false means display
                element.setAttribute("text:is-hidden", "true");
                element.removeAttribute("text:condition");
            }
        }
    }

    @Override
    public void remove() {
        element.getParentNode().removeChild(element);
    }

    @Override
    public String hasUserField(Collection<String> pathsToSearchFor) {
        if (pathsToSearchFor == null || pathsToSearchFor.isEmpty()) {
            return null;
        }

        if (isTableRow(element)) {
            return hasUserFieldInRowCellsIgnoringNestedTables(pathsToSearchFor);
        }

        var fields = OdtHelper.getNodes(element, "text:user-field-get");
        for(var field : fields) {
            var matched = matchArrayPath(field.getName(), pathsToSearchFor);
            if(matched != null) {
                return matched;
            }
        }
        return null;
    }

    private String hasUserFieldInRowCellsIgnoringNestedTables(Collection<String> pathsToSearchFor) {
        for (Node cell = element.getFirstChild(); cell != null; cell = cell.getNextSibling()) {
            if (!isTableCell(cell)) {
                continue;
            }

            String matched = findUserFieldMatchIgnoringNestedTables(cell, pathsToSearchFor);
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    private String findUserFieldMatchIgnoringNestedTables(Node root, Collection<String> pathsToSearchFor) {
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            Node n = stack.pop();
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            // Stop: Inhalt von verschachtelten Tabellen zählt NICHT für die äußere Row
            if (isTableElement(n) && n != root) {
                continue;
            }

            // User fields
            if (isTextUserFieldGet(n) || isTextVariableGet(n)) {
                String fieldName = getTextNameAttribute(n);
                if (fieldName != null) {
                    String matched = matchArrayPath(fieldName, pathsToSearchFor);
                    if (matched != null) {
                        return matched;
                    }
                }
            }

            // DFS
            for (Node c = n.getLastChild(); c != null; c = c.getPreviousSibling()) {
                stack.push(c);
            }
        }
        return null;
    }

    private static boolean isTableRow(Node n) {
        return TABLE_NS.equals(n.getNamespaceURI()) && "table-row".equals(n.getLocalName());
    }

    private static boolean isTableCell(Node n) {
        return TABLE_NS.equals(n.getNamespaceURI()) && "table-cell".equals(n.getLocalName());
    }

    private static boolean isTableElement(Node n) {
        return TABLE_NS.equals(n.getNamespaceURI()) && "table".equals(n.getLocalName());
    }

    private static boolean isTextUserFieldGet(Node n) {
        return TEXT_NS.equals(n.getNamespaceURI()) && "user-field-get".equals(n.getLocalName());
    }

    private static boolean isTextVariableGet(Node n) {
        return TEXT_NS.equals(n.getNamespaceURI()) && "variable-get".equals(n.getLocalName());
    }

    private static String getTextNameAttribute(Node n) {
        if (!(n instanceof org.w3c.dom.Element el)) {
            return null;
        }
        String v = el.getAttributeNS(TEXT_NS, "name");
        return (v == null || v.isBlank()) ? null : v;
    }

    private static String matchArrayPath(String fieldName, Collection<String> pathsToSearchFor) {
        for (String arrayPath : pathsToSearchFor) {
            if (arrayPath == null || arrayPath.isBlank()) {
                continue;
            }
            if (fieldName.equals(arrayPath)) {
                return arrayPath;
            }

            String prefix = arrayPath + ".";
            if (fieldName.startsWith(prefix)) {
                String remainder = fieldName.substring(prefix.length());
                if (remainder.matches("^\\d+(\\.|$).*")) {
                    continue;
                }
                return arrayPath;
            }
        }
        return null;
    }

    private void replaceWithSpan(String text) {
        var parent = element.getParentNode();
        var dom = (OdfContentDom) parent.getOwnerDocument();
        var span = dom.newOdfElement(TextSpanElement.class);
        span.setTextContent(text);
        parent.insertBefore(span, element);
        parent.removeChild(element);
        element = span;
    }

    private static String toOdfConditionLiteral(JsonNode val) {
        if (val == null || val.isNull() || val.isMissingNode()) {
            return "\"\"";
        }
        if (val.isBoolean()) {
            return val.booleanValue() ? "TRUE" : "FALSE";
        }
        if (val.isNumber()) {
            return val.numberValue().toString();
        }
        // String literal: mit doppelten Anführungszeichen, interne Quotes escapen
        String s = val.asText("").replace("\"", "\\\"");
        return "\"" + s + "\"";
    }

    private record PathValue(String path, JsonNode value) {
    }

    /**
     * Sammle alle JSON Leaf-Pfade in dot-Notation.
     * Beispiele:
     * { "kunde": { "anrede": "HERR" } } -> "kunde.anrede"
     * { "a": [ {"b":1} ] } -> "a.0.b"
     */
    private static void collectJsonPaths(JsonNode node, String currentPath, List<PathValue> out) {
        if (node == null || node.isMissingNode()) {
            return;
        }

        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                String next = currentPath.isEmpty() ? e.getKey() : currentPath + "." + e.getKey();
                collectJsonPaths(e.getValue(), next, out);
            });
            return;
        }

        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                String next = currentPath.isEmpty() ? String.valueOf(i) : currentPath + "." + i;
                collectJsonPaths(node.get(i), next, out);
            }
            return;
        }

        // Leaf (value node)
        if (!currentPath.isEmpty()) {
            out.add(new PathValue(currentPath, node));
        }
    }

    /**
     * Checks if the ODT element has a conditional attribute specified.
     *
     * @return true if the element contains a non-blank "text:condition" attribute; false otherwise
     */
    boolean isConditional() {
        return StringUtils.isNotBlank(element.getAttribute("text:condition"));
    }
}
