package io.github.flaechsig.blocpress.render;

/** Art eines Eintrags in production (ADR-0018, REQ-0036); Namen wie in der Workbench. */
public enum TemplateType {
    /** Vorlage, die per Namen gerendert wird. */
    TEMPLATE,
    /** Baustein, der in Vorlagen eingebunden wird. */
    BAUSTEIN
}
