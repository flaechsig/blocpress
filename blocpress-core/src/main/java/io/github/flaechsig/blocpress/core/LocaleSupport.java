package io.github.flaechsig.blocpress.core;

import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Prueft, ob Sprachdaten fuer eine Locale zur Laufzeit wirklich vorhanden sind.
 *
 * <p>Hintergrund: Ein GraalVM-Native-Image enthaelt nur die beim Build eingebundenen
 * Locales. Fuer alle anderen liefert {@link DecimalFormatSymbols#getInstance(Locale)}
 * stillschweigend die Root-Symbole (en-Format: {@code 500,000} statt {@code 500.000}).
 * In der JVM sind dagegen alle Locales vorhanden.</p>
 */
public final class LocaleSupport {

    /** Ersatzsprache, wenn weder Vorlage noch Aufrufer eine Sprache vorgeben (bisheriges Verhalten). */
    public static final Locale FALLBACK_LOCALE = Locale.GERMANY;

    /**
     * Bewusst kein {@code static final}-Initialisierer: wuerde die Klasse beim Native-Build
     * initialisiert, enthielte das Set die Locales der Build-JVM statt die des Images.
     */
    private static volatile Set<Locale> available;

    private LocaleSupport() { /* utility */ }

    /**
     * @return {@code true}, wenn fuer die Locale Zahlenformat-Daten vorhanden sind. Eine Locale
     * ohne Land (z.B. {@code de}) gilt als verfuegbar, wenn ihre Sprache verfuegbar ist.
     */
    public static boolean isAvailable(Locale locale) {
        if (locale == null || locale.getLanguage().isEmpty()) {
            return false;
        }
        Set<Locale> all = available();
        if (all.contains(locale)) {
            return true;
        }
        return locale.getCountry().isEmpty()
                && all.stream().anyMatch(l -> l.getLanguage().equals(locale.getLanguage()));
    }

    /** @return Anzahl der zur Laufzeit verfuegbaren Locales (fuer Diagnose-Logs). */
    public static int availableCount() {
        return available().size();
    }

    private static Set<Locale> available() {
        Set<Locale> result = available;
        if (result == null) {
            result = Arrays.stream(DecimalFormatSymbols.getAvailableLocales())
                    .collect(Collectors.toUnmodifiableSet());
            available = result;
        }
        return result;
    }
}
