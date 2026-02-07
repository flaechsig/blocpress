package org.blocpress.renderer;

/**
 * Legt das Ausgabeformat für das Druckstück fest
 */
public enum OutputFormat {
    ODT("odt"),
    PDF("pdf"),
    RTF("rtf");

    private final String suffix;

    OutputFormat(String suffix) {
        this.suffix = suffix;
    }

    public String getSuffix() {
        return suffix;
    }
}
