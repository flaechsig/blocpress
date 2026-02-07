package org.blocpress.renderer;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.odftoolkit.odfdom.doc.OdfTextDocument;

import java.io.ByteArrayOutputStream;
import java.net.URL;

@Slf4j
public class RenderEngine {
    private static final String STYLE_NS = "urn:oasis:names:tc:opendocument:xmlns:style:1.0";
    private static final String TEXT_NS = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";

    /**
     * Rendert ein ODT-Template (OpenDocument Text) durch Ersetzen von Platzhaltern
     * mit den übergebenen JSON-Daten und gibt das resultierende ODT-Dokument als Byte-Array zurück.
     *
     * <p><b>Fachlicher Kontext:</b>
     * Diese Methode ist Teil eines Template-Engines für die dynamische Generierung von Dokumenten
     * (z.B. Briefe, Rechnungen, Berichte) im ODT-Format. Die JSON-Daten enthalten Schlüssel-Wert-Paare,
     * die den im Template definierten Platzhaltern (Variablenfeldern) entsprechen. Die Platzhalter
     * werden im ODT-Dokument durch die tatsächlichen Werte ersetzt, wobei verschachtelte JSON-Strukturen
     * zuvor in eine flache Map transformiert werden, um komplexe Datenhierarchien abzubilden.
     * Beispiel: Ein JSON-Objekt {@code {"kunde": {"name": "Max Mustermann"}}}
     * wird zu einer flachen Map mit dem Schlüssel {@code "kunde.name"}.</p>
     *
     * <p><b>Beispiel:</b>
     * <pre>
     *   byte[] template = Files.readAllBytes(Paths.get("vorlage.odt"));
     *   ObjectMapper mapper = new ObjectMapper();
     *   JsonNode data = mapper.readTree("{\"name\": \"Max Mustermann\", \"datum\": \"2025-10-18\"}");
     *   byte[] renderedDocument = renderTemplate(template, data);
     * </pre></p>
     *
     * @param template Das ODT-Template als Byte-Array, das Platzhalter im Format {@code ${schlüssel}} enthält.
     *                 Darf nicht {@code null} sein.
     * @param data     JSON-Daten als {@link JsonNode}, die die Werte für die Platzhalter im Template enthalten.
     *                 Unterstützt flache und verschachtelte Strukturen. Darf nicht {@code null} sein.
     * @return Byte-Array des gerenderten ODT-Dokuments mit ersetzten Platzhaltern.
     * @throws IllegalArgumentException wenn {@code template} oder {@code data} {@code null} ist.
     * @see OdfTextDocument für Details zum ODT-Dokumentenmodell.
     */
    @SneakyThrows
    public static byte[] renderTemplate(@NonNull URL template, @NonNull JsonNode data) {
        byte[] output;
        TemplateDocument doc = TemplateDocument.getInstance(template);

        expandTextBlocks(doc);
        processConditions(doc, data);
        processLoops(doc, data);
        replaceFieldsWithStaticText(doc, data);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.save(out);
            output = out.toByteArray();
        }

        return output;
    }


    /**
     * Expands text blocks in the given template document by processing included sections.
     * If a section has a reference to an external URL, the referenced template document
     * is loaded, merged into the current document, and the included section is replaced
     * with its expanded content.
     *
     * The method skips sections without a URL reference.
     *
     * @param doc The template document to expand. Cannot be null.
     *            The document may include references to external text blocks to be merged.
     */
    private static void expandTextBlocks(@NonNull TemplateDocument doc) {
        var includedTextBlocks = doc.collectIncludedTextBlocks();

        for (var section : includedTextBlocks) {
            var url = section.getUrl(doc.getUrl());
            if (url == null) {
                continue;
            }
            var tbDocument = TemplateDocument.load(url);
            doc.merge(tbDocument, section);
        }
    }

    /**
     * Processes conditional document elements by replacing placeholders with static values, evaluating conditions
     * and potential remove the element if condition does not match.
     *
     * @param doc The template document to process
     */
    private static void processConditions(@NonNull TemplateDocument doc, JsonNode data) {
        for (var conditionalTemplateElement : doc.collectConditionalTemplateElements()) {
                conditionalTemplateElement.resolveCondition(data);
        }
    }

    /**
     * Replaces user-defined fields in the template document with static text values from the provided data.
     *
     * @param doc The template document to process
     * @param data The JSON data containing field values
     */
    private static void replaceFieldsWithStaticText(@NonNull TemplateDocument doc, JsonNode data) {
        for (var field : doc.collectUserFields()) {
            var newValue = getFieldValue(field.getName(), data);
            doc.setFieldValue(field, newValue);
        }
    }

    /**
     * Retrieves the value of a field from the provided JSON data.
     *
     * @param dotPath The dot-separated path to the field
     * @param data The JSON data containing field values
     * @return The value of the field, or null if not found
     */
    public static String getFieldValue(String dotPath, JsonNode data) {
        String pointer = "/" + dotPath.replace(".", "/");
        JsonNode n = data.at(pointer);

        if (n == null || n.isMissingNode()) return null;
        if (n.isTextual()) return n.textValue();
        if (n.isBoolean()) return n.booleanValue() ? "TRUE" : "FALSE";
        if (n.isNumber()) return n.numberValue().toString();

        return n.toString();
    }

    /**
     * Processes loop elements in the template document by duplicating them for each item in the specified array path.
     * After duplication, all loop-references inside the clone are indexed:
     * policy_holder.full_name  -> policy_holder.0.full_name (clone #0)
     */
    private static void processLoops(TemplateDocument doc, JsonNode data) throws Exception {
        var elements = doc.findRepeatGroups(data);

        for (var element : elements.entrySet()) {
            handleGenericLoop(doc, element.getKey(), element.getValue(), data);
        }
    }

    /**
     * The repeatable element will be duplicated for each item in the specified array path.
     * After duplication, all loop-references inside the clone are indexed:
     * customer.full_name  -> customer.0.full_name (clone #0)
     * customer.full_name  -> customer.1.full_name (clone #1)
     */
    private static void handleGenericLoop(@NonNull TemplateDocument doc, @NonNull TemplateElement elementToExpand, @NonNull String arrayPath, @NonNull JsonNode data) {
        JsonNode arrayNode = data.at("/" + arrayPath.replace(".", "/"));
        if (arrayNode == null || !arrayNode.isArray()) {
            return;
        }

        for (int index = 0; index < arrayNode.size(); index++) {
            TemplateElement duplicated = doc.duplicate(elementToExpand);
            final String prefix = arrayPath + ".";

            for (var field : duplicated.collectUserFields()) {
                String name = field.getName();
                String updated = indexLoopReferenceIfNeeded(name, prefix, index);
                field.setName(updated);
            }
        }
        doc.removeChild(elementToExpand);
    }

    /**
     * Only index "loop references", not already indexed paths.
     * Example:
     * - prefix="policy_holder.", name="policy_holder.full_name" -> "policy_holder.0.full_name"
     * - prefix="policy_holder.", name="policy_holder.0.full_name" -> unchanged
     */
    private static String indexLoopReferenceIfNeeded(String name, String prefix, int index) {
        if (StringUtils.isBlank(name) || StringUtils.isBlank(prefix)) {
            return name;
        }
        if (!name.startsWith(prefix)) {
            return name;
        }

        String remainder = name.substring(prefix.length());

        // already indexed? => keep
        if (remainder.matches("^\\d+(\\.|$).*")) {
            return name;
        }

        return prefix + index + "." + remainder;
    }
}
