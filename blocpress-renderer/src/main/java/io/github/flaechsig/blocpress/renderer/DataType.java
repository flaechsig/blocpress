package io.github.flaechsig.blocpress.renderer;

/**
 * Datentypen für User-Field-Werte in ODT-Templates.
 *
 * <p><b>Design-Referenzen:</b></p>
 * <ul>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-tf-5">TF-5: Dokument generieren</a> (Feldtyp-Erkennung)</li>
 * </ul>
 */
public enum DataType {
    DATE, FLOAT, CURRENCY, UNKNOWN
}
