package io.github.flaechsig.blocpress.core;

import java.net.URL;

/**
 * A template section represents a textblock or template from an external source which can imported into the document.
 *
 * <p><b>Design-Referenzen:</b></p>
 * <ul>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-e-2">E-2: Baustein</a></li>
 *   <li>SDC: <a href="docs/Solution_Design_Concept.adoc#sdc-ia-baustein">Baustein (Information Architecture)</a></li>
 * </ul>
 */
public interface TemplateSectionElement extends TemplateElement {

    /**
     * Returns the URL associated with this template section, resolved against the provided base URL (from main template).
     *
     * @param baseURL The base URL to resolve against.
     * @return The resolved URL of the template section.
     */
    URL getUrl(URL baseURL);
}
