package io.github.flaechsig.blocpress.render;

/**
 * Thrown when a template cannot be found or is not approved for rendering.
 */
public class TemplateNotFoundException extends RuntimeException {
    public TemplateNotFoundException(String message) {
        super(message);
    }
}
