package io.github.flaechsig.blocpress.core;

import java.net.URL;
import java.util.function.Function;

/**
 * Regel, die zu einem verknuepften Abschnitt ({@code text:section-source}) den einzusetzenden
 * Baustein liefert (ADR-0018).
 *
 * <p>Wer Vorlagen aus fremder Hand rendert, uebergibt eine strenge Regel wie
 * {@link #byName(Function)} oder {@link #rejectAll()}; {@link #byUrl()} oeffnet die Verknuepfung
 * und ist nur fuer vertrauenswuerdige Vorlagen gedacht.</p>
 */
@FunctionalInterface
public interface TextBlockResolver {

    /**
     * @param section     der verknuepfte Abschnitt
     * @param templateUrl URL der Vorlage, die den Abschnitt enthaelt (kann {@code null} sein)
     * @return der einzusetzende Baustein, oder {@code null}, wenn der Abschnitt unveraendert bleibt
     * @throws TextBlockRejectedException wenn der Abschnitt nicht eingesetzt werden darf
     */
    TemplateDocument resolve(TemplateSectionElement section, URL templateUrl);

    /**
     * Bisheriges Verhalten: die Verknuepfung als Datei oder URL oeffnen. Nur fuer Vorlagen, deren
     * Verknuepfungen vertrauenswuerdig sind.
     */
    static TextBlockResolver byUrl() {
        return (section, templateUrl) -> {
            URL url = section.getUrl(templateUrl);
            return url == null ? null : TemplateDocument.load(url);
        };
    }

    /** Lehnt jede Verknuepfung ab (Vorlagen, die der Aufrufer mitschickt). */
    static TextBlockResolver rejectAll() {
        return (section, templateUrl) -> {
            if (isBlank(section.getHref())) {
                return null;
            }
            throw new TextBlockRejectedException("Templates sent with the request must not link building blocks");
        };
    }

    /**
     * Setzt Bausteine per Namen ein: der Name kommt aus einem Pfad, der auf
     * {@code /bausteine/{name}.odt} endet ({@link TextBlocks#nameOf(String)}); den Inhalt liefert
     * {@code lookup}. Die Verknuepfung selbst wird nie geoeffnet.
     *
     * @param lookup liefert den Inhalt (ODT) des Bausteins oder {@code null}, wenn es ihn nicht gibt
     */
    static TextBlockResolver byName(Function<String, byte[]> lookup) {
        return (section, templateUrl) -> {
            String href = section.getHref();
            if (isBlank(href)) {
                return null;
            }
            String name = TextBlocks.nameOf(href).orElseThrow(() ->
                    new TextBlockRejectedException("Linked section does not reference a building block"));
            byte[] content = lookup.apply(name);
            if (content == null) {
                throw new TextBlockRejectedException("Building block not available: " + name);
            }
            return TemplateDocument.load(content);
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
