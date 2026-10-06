package io.github.flaechsig.blocpress.render;

/**
 * Thrown when a template cannot be found in the production schema.
 */
public class TemplateNotFoundException extends RuntimeException {
    public TemplateNotFoundException(String message) {
        super(message);
    }
}
