package io.github.flaechsig.blocpress.core;

/**
 * Legt das Ausgabeformat für das Druckstück fest.
 *
 * <p><b>Design-Referenzen:</b></p>
 * <ul>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-c-5">C-5: Export-Formate (ODT, PDF, RTF)</a></li>
 * </ul>
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
