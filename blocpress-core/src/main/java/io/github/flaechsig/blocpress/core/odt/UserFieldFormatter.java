package io.github.flaechsig.blocpress.core.odt;

import io.github.flaechsig.blocpress.core.DataType;
import io.github.flaechsig.blocpress.core.LocaleSupport;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.odftoolkit.odfdom.doc.OdfTextDocument;
import org.odftoolkit.odfdom.dom.OdfContentDom;
import org.odftoolkit.odfdom.pkg.OdfElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper für text:user-field-get / text:variable-get.
 * Liest den Rohwert (office:value / office:value-type oder TextContent),
 * ermittelt einen passenden number-style (falls vorhanden) und liefert
 * den formatierten String zurück oder ersetzt das Feld durch ein span.
 *
 * <p><b>Design-Referenzen:</b></p>
 * <ul>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-tf-5">TF-5: Dokument generieren</a> (Schritt 6: User-Fields befüllen und formatieren)</li>
 * </ul>
 */
public final class UserFieldFormatter {

    private static final Logger log = LoggerFactory.getLogger(UserFieldFormatter.class);

    private static final String STYLE_NS = "urn:oasis:names:tc:opendocument:xmlns:style:1.0";

    /** Locales, fuer die bereits gewarnt wurde (Warnung einmal je Locale statt je Feld). */
    private static final Set<Locale> WARNED_LOCALES = ConcurrentHashMap.newKeySet();

    private UserFieldFormatter() { /* utility */ }

    /**
     * Liest und formatiert den Wert eines Feldes als String (ohne DOM-Änderung).
     *
     * @param document    das ODF-Dokument
     * @param field       das Feld-Element
     * @param officeValue der Wert, der entsprechend dem Feld-Typ formatiert werden soll
     * @return der formatierte String (Fallback: der Rohtext)
     */
    public static String formatUserFieldValue(OdfTextDocument document, OdfElement field, Object officeValue) {
        return formatUserFieldValue(document, field, officeValue, LocaleSupport.FALLBACK_LOCALE);
    }

    /**
     * Wie {@link #formatUserFieldValue(OdfTextDocument, OdfElement, Object)}, mit einstellbarer
     * Ersatzsprache.
     *
     * @param defaultLocale Sprache fuer Number-/Date-Styles, die selbst keine Sprache
     *                      ({@code number:language}) angeben. Eine Sprachangabe im Style hat Vorrang.
     */
    public static String formatUserFieldValue(OdfTextDocument document, OdfElement field, Object officeValue,
                                              @NonNull Locale defaultLocale) {
        if (document == null || field == null || officeValue == null || StringUtils.isBlank(officeValue.toString())) {
            return "";
        }

        // 1) Rohwert lesen: office:value (präferiert) oder Text-Inhalt
        DataType officeValueType = findFieldType(document, field);
        String raw = officeValue.toString().trim();
        String styleName = field.getAttributeNS(STYLE_NS, "data-style-name");

        // 2) Falls office:value-type float oder numeric, parsen wir als Zahl
        return switch (officeValueType) {
            case FLOAT -> formatNumber(document, styleName, raw, defaultLocale);
            case CURRENCY -> formatNumber(document, styleName, raw, defaultLocale);
            case DATE -> formatDate(document, styleName, raw, defaultLocale);
            default -> raw;
        };
    }

    private static DataType findFieldType(@NonNull OdfTextDocument document, @NonNull OdfElement field) {
        try {
            OdfContentDom contentDom = document.getContentDom();
            Document stylesDom = document.getStylesDom();

            // 1) Direkt aus dem übergebenen Feld (user-field-get) den style:data-style-name holen
            String dataStyleName = field.getAttributeNS(STYLE_NS, "data-style-name");
            if (StringUtils.isNotBlank(dataStyleName)) {
                DataType detected = detectTypeFromStyle(contentDom, stylesDom, dataStyleName);
                if (detected != null) {
                    return detected;
                }
            }

            return DataType.UNKNOWN;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Ermittelt den Typ ("date", "currency", "float", ...) anhand des Style-Namens.
     * Sucht sowohl in contentDom als auch in stylesDom nach passenden Style-Elementen.
     */
    private static DataType detectTypeFromStyle(OdfContentDom contentDom, Document stylesDom, String styleName) {
        if (StringUtils.isBlank(styleName) || contentDom == null || stylesDom == null) return null;

        // Liste der relevanten Style-Tags und die zu erwartende Rückgabe
        Map<String, DataType> tagToType = Map.of(
                "date:date-style", DataType.DATE,
                "number:date-style", DataType.DATE,
                "number:time-style", DataType.DATE,
                "number:number-style", DataType.FLOAT,
                "number:percentage-style", DataType.FLOAT,
                "number:currency-style", DataType.CURRENCY
        );

        for (Map.Entry<String, DataType> e : tagToType.entrySet()) {
            String tag = e.getKey();
            DataType expectedType = e.getValue();

            // content.xml prüfen
            NodeList nl = contentDom.getElementsByTagName(tag);
            for (int i = 0; i < nl.getLength(); i++) {
                var node = nl.item(i);
                if (!(node instanceof Element elem)) {
                    continue;
                }
                if (styleName.equals(elem.getAttribute("style:name"))) {
                    return expectedType;
                }
            }

            // styles.xml prüfen
            nl = stylesDom.getElementsByTagName(tag);
            for (int i = 0; i < nl.getLength(); i++) {
                var node = nl.item(i);
                if (!(node instanceof Element elem)) {
                    continue;
                }
                if (styleName.equals(elem.getAttribute("style:name"))) {
                    return expectedType;
                }
            }
        }


        return DataType.UNKNOWN;
    }

    @SneakyThrows
    private static String formatDate(OdfTextDocument document, String styleName, String raw, Locale defaultLocale) {
        if (StringUtils.isBlank(raw)) return "";

        List<DateTimeFormatter> parseCandidates = List.of(
                DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd.MM.yyyy['T'HH:mm[:ss]]"),
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"),
                DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        );

        TemporalAccessor parsed = null;
        for (DateTimeFormatter fmt : parseCandidates) {
            try {
                parsed = fmt.parseBest(raw, LocalDateTime::from, LocalDate::from, OffsetDateTime::from, LocalTime::from);
                break;
            } catch (Exception ignored) {
            }
        }
        if (parsed == null) {
            return raw;
        }

        DateTimeFormatter outFmt = buildDateFormatter(document, styleName, defaultLocale);

        try {
            if (parsed.query(TemporalQueries.localDate()) != null) {
                LocalDate ld = LocalDate.from(parsed);
                return outFmt.format(ld);
            } else if (parsed.query(TemporalQueries.localDate()) == null && parsed.query(TemporalQueries.localTime()) != null) {
                return raw;
            } else if (parsed.query(TemporalQueries.offset()) != null) {
                LocalDate ld = OffsetDateTime.from(parsed).toLocalDate();
                return outFmt.format(ld);
            } else {
                return raw;
            }
        } catch (Exception e) {
            return raw;
        }
    }

    @SneakyThrows
    private static DateTimeFormatter buildDateFormatter(OdfTextDocument document, String styleName, Locale defaultLocale) {
        DateTimeFormatter fallback = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        if (document == null || StringUtils.isBlank(styleName)) {
            return fallback;
        }

        OdfContentDom contentDom = document.getContentDom();
        Document stylesDom = document.getStylesDom();

        Element styleElement = findDateStyleElement(contentDom, styleName);
        if (styleElement == null && stylesDom != null) {
            styleElement = findDateStyleElement(stylesDom, styleName);
        }
        if (styleElement == null) {
            return fallback;
        }

        Locale locale = resolveLocale(styleElement, defaultLocale);

        StringBuilder pattern = new StringBuilder();
        var children = styleElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element child)) continue;
            boolean isLong = "long".equals(child.getAttribute("number:style"));

            switch (child.getTagName()) {
                case "number:day" -> pattern.append(isLong ? "dd" : "d");
                case "number:month" -> pattern.append(isLong ? "MM" : "M");
                case "number:year" -> pattern.append(isLong ? "yyyy" : "yy");
                case "number:hours" -> pattern.append(isLong ? "HH" : "H");
                case "number:minutes" -> pattern.append(isLong ? "mm" : "m");
                case "number:seconds" -> pattern.append(isLong ? "ss" : "s");
                case "number:text" -> pattern.append("'").append(child.getTextContent()).append("'");
            }
        }

        if (pattern.isEmpty()) {
            return fallback;
        }
        return DateTimeFormatter.ofPattern(pattern.toString()).withLocale(locale);
    }

    private static Element findDateStyleElement(Document dom, String styleName) {
        for (String tag : List.of("number:date-style", "date:date-style", "number:time-style")) {
            NodeList nl = dom.getElementsByTagName(tag);
            for (int i = 0; i < nl.getLength(); i++) {
                if (nl.item(i) instanceof Element elem
                        && styleName.equals(elem.getAttribute("style:name"))) {
                    return elem;
                }
            }
        }
        return null;
    }

    private static String formatNumber(OdfTextDocument document, String style, String value, Locale defaultLocale) {
        if (StringUtils.isBlank(style)) {
            return value;
        }
        Double numericValue;
        String normalized = value.replace("\u00A0", " "); // NBSP zu Leerzeichen

        try {
            numericValue = Double.valueOf(normalized);
        } catch (NumberFormatException ex) {
            //  Versuch den Text zu normalisieren und als Zahl zu interpretieren
            if (normalized.contains(",") && normalized.contains(".")) {
                // wahrscheinlich deutsches grouping "1.234,56"
                normalized = normalized.replace(".", "").replace(",", ".");
            } else if (normalized.contains(",")) {
                normalized = normalized.replace(",", ".");
            }
            numericValue = Double.valueOf(normalized);
        }

        // Versuch DecimalFormat aus number-style zu erzeugen
        DecimalFormat df = findDecimalFormatForStyle(document, style, defaultLocale);

        return df.format(numericValue);
    }

    @SneakyThrows
    private static DecimalFormat findDecimalFormatForStyle(OdfTextDocument document, String styleName, Locale defaultLocale) {
        OdfContentDom contentDom = document.getContentDom();
        Document stylesDom = document.getStylesDom();

        String[] styleElements = {"number:number-style", "number:percentage-style", "number:currency-style"};
        Map<String, NumberStyle> styleNodes = new HashMap<>();

        for (String style : styleElements) {
            NodeList nl = contentDom.getElementsByTagName(style);
            for (int i = 0; i < nl.getLength(); i++) {
                var item = (Element) nl.item(i);
                styleNodes.put(item.getAttribute("style:name"), createNumberStyle(item, defaultLocale));
            }
            nl = stylesDom.getElementsByTagName(style);
            for (int i = 0; i < nl.getLength(); i++) {
                var item = (Element) nl.item(i);
                // only add if absent to let content.xml override styles.xml when names collide
                styleNodes.putIfAbsent(item.getAttribute("style:name"), createNumberStyle(item, defaultLocale));
            }
        }

        return buildDecimalFormatFromNumberStyleElement(styleNodes.get(styleName));
    }

    /**
     * Sprache eines Number-/Date-Styles: {@code number:language}/{@code number:country} aus der
     * Vorlage haben Vorrang; nur wenn der Style keine Sprache nennt, gilt {@code defaultLocale}.
     * Fehlen die Sprachdaten zur Laufzeit (Native-Image), wird laut gewarnt statt still auf das
     * en-Format zurueckzufallen.
     */
    static Locale resolveLocale(Element style, Locale defaultLocale) {
        String language = style.getAttribute("number:language");
        if (StringUtils.isBlank(language)) {
            return defaultLocale;
        }
        Locale locale = Locale.of(language, StringUtils.defaultString(style.getAttribute("number:country")));
        if (!LocaleSupport.isAvailable(locale) && WARNED_LOCALES.add(locale)) {
            log.warn("Vorlage verlangt Sprache '{}', fuer die keine Sprachdaten vorhanden sind — "
                    + "Zahlen/Daten werden im Root-Format (en) ausgegeben. Native-Image mit passendem "
                    + "quarkus.locales bauen.", locale.toLanguageTag());
        }
        return locale;
    }

    private static NumberStyle createNumberStyle(Element elem, Locale defaultLocale) {
        Locale locale = resolveLocale(elem, defaultLocale);
        int decimalPlaces = 0;
        int minimalDecimalPlaces = 0;
        int minIntegerDigits = 1;
        boolean grouping = false;
        String symbol = "";

        var children = elem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Element child = (Element) children.item(i);
            try {
                if ("number:number".equals(child.getTagName())) {
                    var textContent = child.getAttribute("number:decimal-places");
                    decimalPlaces = StringUtils.isBlank(textContent) ? 0 : Integer.parseInt(textContent);
                    textContent = child.getAttribute("number:min-decimal-places");
                    minimalDecimalPlaces = StringUtils.isBlank(textContent) ? 0 : Integer.parseInt(textContent);
                    textContent = child.getAttribute("number:min-integer-digits");
                    minIntegerDigits = StringUtils.isBlank(textContent) ? 0 : Integer.parseInt(textContent);
                    textContent = child.getAttribute("number:grouping");
                    grouping = !StringUtils.isBlank(textContent) && Boolean.parseBoolean(textContent);
                }
                if ("number:text".equals(child.getTagName())) {
                    symbol = child.getTextContent();
                }
                if ("number:currency-symbol".equals(child.getTagName())) {
                    symbol = " " + child.getTextContent();
                }
            } catch (RuntimeException e) {
                log.error("Problems rendering {} Content: '{}'", child.getTagName(), child.getTextContent());
                throw e;
            }
        }
        return new NumberStyle(minIntegerDigits, decimalPlaces, minimalDecimalPlaces, grouping, symbol, locale);
    }


    private static DecimalFormat buildDecimalFormatFromNumberStyleElement(NumberStyle style) {
        DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(style.locale());
        return new DecimalFormat(style.formatString(), dfs);
    }

    /**
     * Represents the style information for formatting numeric values.
     * <p>
     * This record is used to encapsulate formatting configurations, such as the
     * number of minimum integer digits, number of decimal places, and locale-specific
     * details like associated symbols, country, and language. It is primarily
     * designed for cases where formatting needs are tailored to specific styles.
     * <p>
     * Fields:
     * - `minIntegerDigits`: Specifies the minimum number of digits to be displayed
     * in the integer part of a number.
     * - `decimalPlaces`: Defines the total number of decimal places used for displaying
     * the fractional portion of a number.
     * - `minDecimalPlaces`: Defines the minimum number of decimal places required
     * for the fractional portion of a number.
     * - `symbol`: Represents a formatting symbol, such as a percentage or currency
     * symbol, associated with the number style.
     * - `locale`: The locale used for locale-specific formatting (decimal and grouping separators).
     */
    record NumberStyle(
            int minIntegerDigits,
            int decimalPlaces,
            int minDecimalPlaces,
            boolean grouping,
            String symbol,
            Locale locale
    ) {
        /**
         * Formats and returns a string representation of a numeric value based on
         * the configuration defined in the containing record. The method utilizes
         * properties such as minimum integer digits, decimal places, and locale-specific
         * settings for constructing the output string.
         *
         * @return a formatted string representation of a numeric value adhering to the
         * specified number style and locale settings.
         */
        public String formatString() {
            String pattern = "";

            // Integer part
            if (grouping) {
                pattern = "#,##";
            }
            pattern += StringUtils.rightPad("", Math.max(1, minIntegerDigits), '0');

            // Fractional part
            if (decimalPlaces > 0) {
                pattern += '.';
                // mandatory minimum decimal places
                pattern += StringUtils.rightPad("", Math.max(1, minDecimalPlaces), '0');
                // optional additional decimal places up to decimalPlaces
                pattern += StringUtils.rightPad("", Math.max(0, decimalPlaces - minDecimalPlaces), '#');
            }

            // Symbol
            if (StringUtils.isNotBlank(symbol)) {
                pattern += "'" + symbol + "'";
            }
            return pattern;
        }

    }
}
