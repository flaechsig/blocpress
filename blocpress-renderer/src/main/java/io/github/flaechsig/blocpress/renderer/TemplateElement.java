package io.github.flaechsig.blocpress.renderer;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.List;

/**
 * Interface for individual template elements (User-Fields, Sections, Spans).
 *
 * <p><b>Design-Referenzen:</b></p>
 * <ul>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-tf-5">TF-5: Dokument generieren</a> (User-Fields, IF-Bedingungen, Wiederholungsgruppen)</li>
 *   <li>SDC: <a href="docs/Solution_Design_Concept.adoc#sdc-ia-userfield">User-Field (Information Architecture)</a></li>
 * </ul>
 */
public interface TemplateElement {

    /**
     * Retrieves the name of the template element.
     *
     * @return the name of the template element as a string
     */
    String getName();

    /**
     * Sets the name of the template element.
     *
     * @param name the new name for the template element
     */
    void setName(String name);

    /**
     * Gathers a list of user-defined fields associated with the template element.
     *
     * @return a list of TemplateElement objects representing the user-defined fields
     */
    List<TemplateElement> collectUserFields();

    /**
     * Checks if the TemplateElement matches the condition for provided data
     *
     * @return true if no condition is set or the condition matches for the provided data
     */
    boolean matchCondition(JsonNode data);

    /**
     * Resolves the condition for the template element based on the provided data.
     * If the condition matches, the element is resolved and its content is updated accordingly.
     *
     * @param data the JSON data to evaluate the condition against
     */
    void resolveCondition(JsonNode data);

    /**
     * Removes the template element from its parent node.
     */
    void remove();

    /**
     * Checks if the TemplateElement has any user-defined fields that match the provided array paths.
     *
     * @param pathsToSearchFor a collection of array paths to check against
     * @return the matched pathelement
     */
    String hasUserField(Collection<String> pathsToSearchFor);
}
