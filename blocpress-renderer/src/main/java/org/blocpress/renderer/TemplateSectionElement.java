package org.blocpress.renderer;

import java.net.URL;

/**
 * A template section represents a textblock or template from an external source which can imported into the document.
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
