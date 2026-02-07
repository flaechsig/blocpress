package org.blocpress.renderer;

import com.fasterxml.jackson.databind.JsonNode;
import org.blocpress.renderer.odt.OdtTemplateDocument;
import org.blocpress.renderer.odt.OdtTemplateSectionElement;

import java.io.OutputStream;
import java.net.URL;
import java.util.*;

/**
 * Base class for template documents.
 */
public interface TemplateDocument {
    /**
     * Creates a TemplateDocument instance from a byte array.
     *
     * @param template The byte array representing the template.
     * @return A TemplateDocument instance.
     */
    static TemplateDocument getInstance(URL template) {
        return new OdtTemplateDocument(template);
    }

    /**
     * Loads a template document from the specified URL.
     *
     * @param url The URL from which the template document will be loaded.
     * @return A TemplateDocument instance representing the loaded template.
     */
    static TemplateDocument load(URL url) {
        return new OdtTemplateDocument(url);
    }

    URL getUrl();

    /**
     * Merges the content of the template document into the current section.
     *
     * @param tbDocument The template document to merge.
     * @param section
     */
    void merge(TemplateDocument tbDocument, TemplateSectionElement section);

    /**
     * Determines repetition groups (sections or table rows) based on arrays.
     * Returns: Template element -> arrayPath (e.g., "policy_holder" or "policy_holder.children")
     */
    Map<TemplateElement, String> findRepeatGroups(JsonNode data);

    /**
     * Duplicates a template element, creating a new instance with the same structure an place it
     * directly after the original element.
     *
     * @param original The template element to duplicate.
     * @return A new template element with the same structure as the original.
     */
    TemplateElement duplicate(TemplateElement original);

    /**
     * Saves the template document to an output stream.
     *
     * @param out The output stream to save the document to.
     */
    void save(OutputStream out);

    /**
     * Removes a specified child element from the template document.
     *
     * @param element The child template element to be removed.
     */
    void removeChild(TemplateElement element);

    /**
     * Collects all template elements that are conditionally rendered based on data.
     *
     * @return A list of template elements that are conditionally rendered.
     */
    List<TemplateElement> collectConditionalTemplateElements();

    /**
     * Collects all template elements that are included as text blocks within the template document.
     *
     * @return a list of TemplateElement objects representing text blocks that are included in the template.
     */
    List<OdtTemplateSectionElement> collectIncludedTextBlocks();

    /**
     * @return Collects all TemplateElements which could have references to user data
     */
    List<TemplateElement> collectUserFields();

    /**
     * Sets the value of a user-defined field in the template. The user-defined field will be
     * replaced with a text element containing the provided value.
     *
     * @param userField The template element representing the user-defined field to be updated.
     * @param value     The new value to be assigned to the specified user field.
     */
    void setFieldValue(TemplateElement userField, String value);

    /**
     * @return All data list keys which represent array elements in the template data.
     */
    default Collection<String> getArrayPaths(JsonNode data) {
        Set<String> result = new HashSet<>();
        findArrayPaths("", data, result);
        return result;
    }

    private void findArrayPaths(String currentPath, JsonNode node, Set<String> result) {
        if (node.isArray()) {
            if (!currentPath.isEmpty()) {
                result.add(currentPath);
            }
            for (int i = 0; i < node.size(); i++) {
                findArrayPaths(currentPath, node.get(i), result);
            }
        } else if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String nextPath = currentPath.isEmpty() ? entry.getKey() : currentPath + "." + entry.getKey();
                findArrayPaths(nextPath, entry.getValue(), result);
            }
        }
    }

}
