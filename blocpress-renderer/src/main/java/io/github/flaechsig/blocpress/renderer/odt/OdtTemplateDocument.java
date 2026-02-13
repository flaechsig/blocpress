package io.github.flaechsig.blocpress.renderer.odt;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import io.github.flaechsig.blocpress.renderer.TemplateDocument;
import io.github.flaechsig.blocpress.renderer.TemplateElement;
import io.github.flaechsig.blocpress.renderer.TemplateSectionElement;
import org.odftoolkit.odfdom.doc.OdfTextDocument;
import org.odftoolkit.odfdom.dom.OdfContentDom;
import org.odftoolkit.odfdom.dom.element.text.TextSectionElement;
import org.odftoolkit.odfdom.dom.element.text.TextSpanElement;
import org.odftoolkit.odfdom.pkg.OdfElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ODT-Implementierung von {@link TemplateDocument}. Kapselt ein {@link OdfTextDocument}
 * und stellt Methoden zur Template-Verarbeitung bereit (Textblock-Expansion, Style-Merging,
 * Wiederholungsgruppen, User-Field-Ersetzung).
 *
 * <p><b>Design-Referenzen:</b></p>
 * <ul>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-tf-1">TF-1: Template validieren</a></li>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-tf-5">TF-5: Dokument generieren</a></li>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-ti-3">TI-3: LibreOffice API</a></li>
 * </ul>
 */
public class OdtTemplateDocument implements TemplateDocument {

    private static final Pattern SECTION_PARAMS = Pattern.compile("^\\s*[^()]+\\((.*)\\)\\s*$");
    private static final String TABLE_NS = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";

    private final URL url;
    OdfTextDocument document;

    private static final String OFFICE_NS = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
    private static final String STYLE_NS = "urn:oasis:names:tc:opendocument:xmlns:style:1.0";
    private static final String TEXT_NS = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
    private static final String SVG_NS = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0";
    private static final String DRAW_NS = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0";

    public OdtTemplateDocument(@NonNull URL url) {
        this.url = url;
        try (InputStream in = url.openStream()) {
            document = OdfTextDocument.loadDocument(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public URL getUrl() {
        return url;
    }

    /**
     * Determines repetition groups (sections or table rows) based on arrays.
     * Returns: Template element -> arrayPath (e.g., "policy_holder" or "policy_holder.children")
     */
    @SneakyThrows
    public Map<TemplateElement, String> findRepeatGroups(JsonNode data) {
        Collection<String> arrayPaths = getArrayPaths(data);
        if (arrayPaths.isEmpty()) {
            return Map.of();
        }

        List<TemplateElement> candidates = new ArrayList<>();
        candidates.addAll(OdtHelper.getNodes(document.getContentRoot(), "text:section"));
        candidates.addAll(OdtHelper.getNodes(document.getContentRoot(), "table:table-row"));

        Map<TemplateElement, String> result = new LinkedHashMap<>();

        for (TemplateElement candidate : candidates) {
            var matchedArrayPath = candidate.hasUserField(arrayPaths);
            if (StringUtils.isNotBlank(matchedArrayPath)) {
                result.put(candidate, matchedArrayPath);
            }
        }

        return result;
    }

    /**
     * Duplicates a template element, creating a new instance with the same structure and place it
     * directly after the original element.
     *
     * @param original The template element to duplicate.
     * @return the duplicated template element
     */
    @Override
    public TemplateElement duplicate(TemplateElement original) {
        OdtTemplateElement odtOriginal = (OdtTemplateElement) original;
        OdfElement odfOriginal = odtOriginal.element;

        OdfElement clone = (OdfElement) odfOriginal.cloneNode(true);
        odfOriginal.getParentNode().insertBefore(clone, odfOriginal);
        OdtTemplateElement cloneElement = new OdtTemplateElement(clone);

        // for sections set an unique name
        if (clone instanceof TextSectionElement section) {
            section.setTextNameAttribute("gen_" + System.nanoTime());
        }

        return cloneElement;
    }

    @Override
    @SneakyThrows
    public void save(OutputStream out) {
        document.save(out);
    }

    @Override
    @SneakyThrows
    public void removeChild(TemplateElement element) {
        element.remove();
    }

    /**
     * Collects conditional template elements from document content
     */
    @Override
    @SneakyThrows
    public List<TemplateElement> collectConditionalTemplateElements() {
        String[] tagNames = {"text:section", "text:p", "text:span", "text:conditional-text"};
        List<TemplateElement> elements = new ArrayList<>();

        for (var tagName : tagNames) {
            elements.addAll(
                    OdtHelper.getNodes(document.getContentRoot(), tagName).stream()
                            .filter(OdtTemplateElement::isConditional)
                            .map(e -> (TemplateElement) e)
                            .toList()
            );
        }
        return elements;
    }

    @Override
    @SneakyThrows
    public List<OdtTemplateSectionElement> collectIncludedTextBlocks() {
        return OdtHelper.getNodes(document.getContentRoot(), "text:section-source").stream()
                .map(e -> new OdtTemplateSectionElement(e.element))
                .toList();
    }

    @Override
    @SneakyThrows
    public List<TemplateElement> collectUserFields() {
        List<TemplateElement> fields = new ArrayList<>();

        OdtHelper.getNodes(document.getContentRoot(), "text:user-field-get").stream()
                .forEach(n -> fields.add(n));
        OdtHelper.getNodes(document.getContentRoot(), "text:variable-get").stream()
                .forEach(n -> fields.add(n));

        return fields;
    }

    /**
     * Updates the value of a user-defined field within the template document.
     * Replaces the current field content with the provided value. If the value is null,
     * it will be replaced with an empty string.
     *
     * @param userField the template element representing the user-defined field to be updated; must not be null
     * @param value     the new value to set for the user-defined field; if null, defaults to an empty string
     * @throws NullPointerException if the provided userField is null
     */
    @SneakyThrows
    public void setFieldValue(@NonNull TemplateElement userField, String value) {
        var odfElement = ((OdtTemplateElement) userField).element;
        var newValue = value != null ? value : "";

        var parent = odfElement.getParentNode();
        if (parent == null) {
            // Feld hängt nicht mehr im Baum -> nichts zu ersetzen
            return;
        }

        OdfContentDom dom = (OdfContentDom) parent.getOwnerDocument();
        TextSpanElement span = dom.newOdfElement(TextSpanElement.class);
        span.setTextContent(UserFieldFormatter.formatUserFieldValue(document, odfElement, newValue));

        parent.insertBefore(span, odfElement);
        parent.removeChild(odfElement);
        ((OdtTemplateElement) userField).element = span;
    }

    @Override
    @SneakyThrows
    public void merge(TemplateDocument tbDocument, TemplateSectionElement sectionElement) {
        OdtTemplateSectionElement section = (OdtTemplateSectionElement) sectionElement;
        OdtTemplateDocument source = (OdtTemplateDocument) tbDocument;

        Map<String, String> renameMap = mergeStylesSmart(source);
        Map<String, String> pathMapping = parseSectionNameMapping(section);

        List<Node> nodesToImport = new ArrayList<>();
        var officeText = source.document.getContentRoot();
        var childNodes = officeText.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            var childNode = childNodes.item(i);
            if (childNode.getNodeType() == Node.TEXT_NODE || childNode.getNodeType() == Node.ELEMENT_NODE) {
                nodesToImport.add(childNode);
            }
        }

        // remove existing childs from section
        var wrkSection = section.element.getParentNode();
        Node node;
        while ((node = wrkSection.getFirstChild()) != null) {
            wrkSection.removeChild(node);
        }

        // import document data
        Document targetDom = wrkSection.getOwnerDocument();
        for (var n : nodesToImport) {
            Node imported = targetDom.importNode(n, true);
            rewriteUserFieldNames(imported, pathMapping);
            rewriteStyleReferences(imported, renameMap);
            wrkSection.appendChild(imported);
        }
    }

    /**
     * Extracts mapping from section name.
     *
     * Expected: TemplateName(targetParam1=sourceParam1, targetParam2=sourceParam2)
     * Mapping direction: targetParam -> sourceParam
     */
    private Map<String, String> parseSectionNameMapping(OdtTemplateSectionElement section) {
        Node parent = section.element != null ? section.element.getParentNode() : null;
        if (!(parent instanceof Element parentEl)) {
            return Map.of();
        }

        // LibreOffice stores section name as text:name on the text:section element
        String sectionName = parentEl.getAttributeNS(TEXT_NS, "name");
        if (sectionName == null || sectionName.isBlank()) {
            // fallback if namespace-aware attr lookup fails
            sectionName = parentEl.getAttribute("text:name");
        }
        if (sectionName == null || sectionName.isBlank()) {
            return Map.of();
        }

        Matcher m = SECTION_PARAMS.matcher(sectionName);
        if (!m.matches()) {
            return Map.of();
        }

        String params = m.group(1);
        if (params == null || params.isBlank()) {
            return Map.of();
        }

        Map<String, String> mapping = new LinkedHashMap<>();
        for (String part : params.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;

            int eq = p.indexOf('=');
            if (eq <= 0 || eq >= p.length() - 1) continue;

            String targetParam = p.substring(0, eq).trim();
            String sourceParam = p.substring(eq + 1).trim();

            if (!targetParam.isEmpty() && !sourceParam.isEmpty()) {
                mapping.put(targetParam, sourceParam);
            }
        }
        return mapping;
    }

    private void rewriteUserFieldNames(Node root, Map<String, String> mapping) {
        if (root.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) root;

            boolean isUserFieldGet = TEXT_NS.equals(el.getNamespaceURI()) && "user-field-get".equals(el.getLocalName());
            boolean isVariableGet = TEXT_NS.equals(el.getNamespaceURI()) && "variable-get".equals(el.getLocalName());

            if (isUserFieldGet || isVariableGet) {
                String name = el.getAttributeNS(TEXT_NS, "name");
                if (name == null || name.isBlank()) {
                    name = el.getAttribute("text:name");
                }

                String rewritten = rewriteByPrefixMapping(name, mapping);
                if (rewritten != null && !rewritten.equals(name)) {
                    el.setAttributeNS(TEXT_NS, "text:name", rewritten);
                }
            }
        }

        NodeList nl = root.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node child = nl.item(i);

            // Optional: ignore nested tables completely (prevents accidental rewrites in embedded tables)
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) child;
                if (TABLE_NS.equals(el.getNamespaceURI()) && "table".equals(el.getLocalName())) {
                    continue;
                }
            }

            rewriteUserFieldNames(child, mapping);
        }
    }

    /**
     * Rewrites "target..." to "source..." for each mapping entry.
     * Applies only when the field starts with "target." or equals "target".
     * If multiple targets could match, the longest target wins.
     */
    private String rewriteByPrefixMapping(String fieldName, Map<String, String> mapping) {
        if (fieldName == null || fieldName.isBlank() || mapping.isEmpty()) {
            return fieldName;
        }

        String bestTarget = null;
        for (String target : mapping.keySet()) {
            if (target == null || target.isBlank()) continue;

            boolean matches = fieldName.equals(target) || fieldName.startsWith(target + ".");
            if (!matches) continue;

            if (bestTarget == null || target.length() > bestTarget.length()) {
                bestTarget = target;
            }
        }

        if (bestTarget == null) {
            return fieldName;
        }

        String source = mapping.get(bestTarget);
        if (fieldName.equals(bestTarget)) {
            return source;
        }
        return source + fieldName.substring(bestTarget.length());
    }

    @SneakyThrows
    private Map<String, String> mergeStylesSmart(OdtTemplateDocument source) {
        Map<String, String> renameMap = new HashMap<>();
        String prefix = "TB_" + Long.toHexString(System.nanoTime()) + "_";

        mergeOfficeBlockSmart(source.document.getContentDom(), this.document.getContentDom(), "font-face-decls", prefix, renameMap);
        mergeOfficeBlockSmart(source.document.getContentDom(), this.document.getContentDom(), "automatic-styles", prefix, renameMap);

        mergeOfficeBlockSmart(source.document.getStylesDom(), this.document.getStylesDom(), "font-face-decls", prefix, renameMap);
        mergeOfficeBlockSmart(source.document.getStylesDom(), this.document.getStylesDom(), "automatic-styles", prefix, renameMap);
        mergeOfficeBlockSmart(source.document.getStylesDom(), this.document.getStylesDom(), "styles", prefix, renameMap);

        return renameMap;
    }

    /**
     * Smart template:
     * - if name doesn't exist in target -> import
     * - if exists and definition identical -> skip (reuse master style)
     * - if exists but different -> import renamed copy and store mapping old->new
     */
    @SneakyThrows
    private void mergeOfficeBlockSmart(Document sourceDom,
                                       Document targetDom,
                                       String blockLocalName,
                                       String prefix,
                                       Map<String, String> renameMap) {
        Node sourceBlock = firstByLocalName(sourceDom, OFFICE_NS, blockLocalName);
        Node targetBlock = firstByLocalName(targetDom, OFFICE_NS, blockLocalName);
        if (sourceBlock == null || targetBlock == null) {
            return;
        }

        Map<String, String> targetFingerprintByName = indexStyleFingerprints(targetBlock);

        NodeList children = sourceBlock.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String styleName = getStyleLikeName(child);
            if (styleName == null || styleName.isBlank()) {
                // unnamed -> import as-is
                targetBlock.appendChild(targetDom.importNode(child, true));
                continue;
            }

            String sourceFp = fingerprintStyleNode(child);

            String targetFp = targetFingerprintByName.get(styleName);
            if (targetFp == null) {
                // not present in target -> import
                targetBlock.appendChild(targetDom.importNode(child, true));
                targetFingerprintByName.put(styleName, sourceFp);
                continue;
            }

            if (targetFp.equals(sourceFp)) {
                // identical -> reuse master style, do nothing
                continue;
            }

            // different -> rename + import renamed copy
            String newName = renameMap.computeIfAbsent(styleName, k -> prefix + k);

            Element imported = (Element) targetDom.importNode(child, true);
            imported.setAttributeNS(STYLE_NS, "style:name", newName);

            // style-internal references that might point to other renamed styles
            rewriteStyleAttributesOnElement(imported, renameMap);

            targetBlock.appendChild(imported);
            targetFingerprintByName.put(newName, sourceFp);
        }
    }

    private Map<String, String> indexStyleFingerprints(Node officeBlock) {
        Map<String, String> map = new HashMap<>();
        NodeList nl = officeBlock.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;

            String name = getStyleLikeName(n);
            if (name == null || name.isBlank()) continue;

            map.put(name, fingerprintStyleNode(n));
        }
        return map;
    }


    /**
     * Build a stable-ish fingerprint of a style element ignoring its name.
     * This is used to decide whether master style and source style are identical.
     */
    private String fingerprintStyleNode(Node n) {
        StringBuilder sb = new StringBuilder(2048);
        appendFingerprint(n, sb, true);
        return sha256(sb.toString());
    }

    private void appendFingerprint(Node n, StringBuilder sb, boolean ignoreStyleNameAttr) {
        if (n.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) n;
            sb.append('<').append(el.getNamespaceURI()).append('|').append(el.getLocalName()).append('>');

            // attributes
            var attrs = el.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                var a = attrs.item(i);
                String ans = a.getNamespaceURI();
                String an = a.getLocalName();
                String av = a.getNodeValue();

                if (ignoreStyleNameAttr && STYLE_NS.equals(ans) && "name".equals(an)) {
                    continue; // ignore style:name
                }
                sb.append('@').append(ans).append('|').append(an).append('=').append(av);
            }
        } else if (n.getNodeType() == Node.TEXT_NODE) {
            sb.append('#').append(n.getNodeValue().trim());
        }

        NodeList kids = n.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            appendFingerprint(kids.item(i), sb, ignoreStyleNameAttr);
        }
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(d.length * 2);
            for (byte b : d) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void rewriteStyleReferences(Node root, Map<String, String> renamed) {
        if (renamed == null || renamed.isEmpty()) return;

        if (root.getNodeType() == Node.ELEMENT_NODE) {
            rewriteStyleAttributesOnElement((Element) root, renamed);
        }
        NodeList nl = root.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            rewriteStyleReferences(nl.item(i), renamed);
        }
    }

    private void rewriteStyleAttributesOnElement(Element el, Map<String, String> renamed) {
        // content references
        rewriteIfPresent(el, TEXT_NS, "style-name", renamed);
        rewriteIfPresent(el, TEXT_NS, "list-style-name", renamed);
        rewriteIfPresent(el, DRAW_NS, "style-name", renamed);
        rewriteIfPresent(el, DRAW_NS, "text-style-name", renamed);

        // style definitions references
        rewriteIfPresent(el, STYLE_NS, "parent-style-name", renamed);
        rewriteIfPresent(el, STYLE_NS, "next-style-name", renamed);
        rewriteIfPresent(el, STYLE_NS, "list-style-name", renamed);
        rewriteIfPresent(el, STYLE_NS, "page-layout-name", renamed);
    }

    private void rewriteIfPresent(Element el, String ns, String localAttr, Map<String, String> renamed) {
        String oldVal = el.getAttributeNS(ns, localAttr);
        if (oldVal == null || oldVal.isBlank()) return;

        String newVal = renamed.get(oldVal);
        if (newVal == null) return;

        String qualified = switch (ns) {
            case STYLE_NS -> "style:" + localAttr;
            case TEXT_NS  -> "text:" + localAttr;
            case DRAW_NS  -> "draw:" + localAttr;
            default       -> localAttr;
        };
        el.setAttributeNS(ns, qualified, newVal);
    }

    private Node firstByLocalName(Document dom, String ns, String localName) {
        NodeList nl = dom.getElementsByTagNameNS(ns, localName);
        return nl.getLength() > 0 ? nl.item(0) : null;
    }

    /**
     * Tries to extract a unique name for different style-ish nodes:
     * - style:style -> style:name
     * - text:list-style -> style:name
     * - style:page-layout -> style:name
     * - office:font-face -> style:name (in ODF it's style:name)
     */
    private static String getStyleLikeName(Node n) {
        if (!(n instanceof org.w3c.dom.Element el)) {
            return null;
        }
        String v = el.getAttributeNS(STYLE_NS, "name");
        if (v != null && !v.isBlank()) return v;

        // Some ODF nodes use text:name etc. (rare here), keep as fallback:
        v = el.getAttributeNS(TEXT_NS, "name");
        if (v != null && !v.isBlank()) return v;

        v = el.getAttributeNS(DRAW_NS, "name");
        if (v != null && !v.isBlank()) return v;

        v = el.getAttributeNS(SVG_NS, "name");
        if (v != null && !v.isBlank()) return v;

        return null;
    }

}

