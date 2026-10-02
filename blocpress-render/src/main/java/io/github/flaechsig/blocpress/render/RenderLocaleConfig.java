package io.github.flaechsig.blocpress.render;

import io.github.flaechsig.blocpress.core.LocaleSupport;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Ersatzsprache fuer Zahlen- und Datumsformate ({@code blocpress.render.default-locale},
 * Umgebungsvariable {@code BLOCPRESS_DEFAULT_LOCALE}, BCP-47-Tag wie {@code de-DE}).
 *
 * <p>Greift nur fuer Number-/Date-Styles der Vorlage, die selbst keine Sprache angeben —
 * eine Sprache im Style hat Vorrang. Bewusst unabhaengig von {@code LANG}/{@code user.language}.</p>
 *
 * <p>Wird beim Start geprueft: Fehlen im (Native-)Image die Sprachdaten fuer die eingestellte
 * Sprache, bricht der Start mit klarer Meldung ab, statt stillschweigend im en-Format zu
 * formatieren (Regression 2.5.0).</p>
 */
@Startup
@ApplicationScoped
public class RenderLocaleConfig {

    private static final Logger LOG = LoggerFactory.getLogger(RenderLocaleConfig.class);

    static final String PROPERTY = "blocpress.render.default-locale";

    @ConfigProperty(name = PROPERTY, defaultValue = "de-DE")
    String defaultLocaleTag;

    private Locale defaultLocale;

    /** Fuer Tests ohne CDI. */
    static RenderLocaleConfig of(String languageTag) {
        RenderLocaleConfig config = new RenderLocaleConfig();
        config.defaultLocaleTag = languageTag;
        config.init();
        return config;
    }

    @PostConstruct
    void init() {
        defaultLocale = parseAndValidate(defaultLocaleTag);
        LOG.info("Ersatzsprache fuer Zahlen-/Datumsformate: {} ({} Sprachen verfuegbar)",
                defaultLocale.toLanguageTag(), LocaleSupport.availableCount());
    }

    /** @return die Sprache fuer Number-/Date-Styles ohne eigene Sprachangabe. */
    public Locale defaultLocale() {
        return defaultLocale;
    }

    /**
     * @throws IllegalStateException wenn der Tag ungueltig ist oder die Sprachdaten fehlen
     */
    static Locale parseAndValidate(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            throw new IllegalStateException(PROPERTY + " ist leer — erwartet wird ein BCP-47-Sprach-Tag, z.B. de-DE");
        }
        Locale locale = Locale.forLanguageTag(languageTag.trim().replace('_', '-'));
        if (locale.getLanguage().isEmpty()) {
            throw new IllegalStateException(PROPERTY + "='" + languageTag
                    + "' ist kein gueltiger BCP-47-Sprach-Tag (Beispiel: de-DE, en-US)");
        }
        if (!LocaleSupport.isAvailable(locale)) {
            throw new IllegalStateException(PROPERTY + "='" + languageTag + "': fuer diese Sprache sind keine "
                    + "Sprachdaten vorhanden (" + LocaleSupport.availableCount() + " Sprachen verfuegbar). "
                    + "Zahlen wuerden sonst stillschweigend im en-Format ausgegeben. Native-Image mit "
                    + "passendem quarkus.locales bauen oder BLOCPRESS_DEFAULT_LOCALE aendern.");
        }
        return locale;
    }
}
