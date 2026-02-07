package io.github.flaechsig.blocpress.renderer.odt;

import io.github.flaechsig.blocpress.renderer.DataType;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.odftoolkit.odfdom.doc.OdfTextDocument;
import org.odftoolkit.odfdom.dom.OdfContentDom;
import org.odftoolkit.odfdom.pkg.OdfElement;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Helper für text:user-field-get / text:variable-get.
 * Liest den Rohwert (office:value / office:value-type oder TextContent),
 * ermittelt einen passenden number-style (falls vorhanden) und liefert
 * den formatierten String zurück oder ersetzt das Feld durch ein span.
 */
public final class UserFieldFormatter {

    private static final String STYLE_NS = "urn:oasis:names:tc:opendocument:xmlns:style:1.0";

    private UserFieldFormatter() { /* utility */ }

    /**
     * Liest und formatiert den Wert eines Feldes als String (ohne DOM-Änderung).
     *
     * @param document    das ODF-Dokument
     * @param field       das Feld-Element
     * @param officeValue der Wert, der entsprechend des Feldes formatiert werden soll
     * @return der formatierte String (oder der Rohtext, falls nicht formatiert werden kann)
     */
    public static String formatUserFieldValue(OdfTextDocument document, OdfElement field, Object officeValue) {
        if (document == null || field == null || officeValue == null || StringUtils.isBlank(officeValue.toString())) {
            return "";
        }

        // 1) Rohwert lesen: office:value (präferiert) oder Text-Inhalt
        DataType officeValueType = findFieldType(document, field);
        String raw = officeValue.toString().trim();
        String styleName = field.getAttributeNS(STYLE_NS, "data-style-name");

        // 2) Falls office:value-type float oder numeric, parsen wir als Zahl
        return switch (officeValueType) {
            case FLOAT -> formatNumber(document, styleName, raw);
            case CURRENCY -> formatNumber(document, styleName, raw);
            case DATE -> formatDate(document, styleName, raw);
            default -> raw;
        };
    }

    private static String serialToDateString(double serial) {
        // ODF/LibreOffice serial epoch: 1899-12-30 (serial 0 -> 1899-12-30)
        LocalDate epoch = LocalDate.of(1899, 12, 30);
        long days = (long) Math.floor(serial);
        double frac = serial - days;
        LocalDate date = epoch.plusDays(days);
        // Nur Datum (wie gewünscht): Ausgabeformat "yyyy-MM-dd"
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
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
                if (!(node instanceof Element)) {
                    continue;
                }
                Element elem = (Element) node;
                if (styleName.equals(elem.getAttribute("style:name"))) {
                    return expectedType;
                }
            }

            // styles.xml prüfen
            nl = stylesDom.getElementsByTagName(tag);
            for (int i = 0; i < nl.getLength(); i++) {
                var node = nl.item(i);
                if (!(node instanceof Element)) continue;
                Element elem = (Element) node;
                if (styleName.equals(elem.getAttribute("style:name"))) {
                    return expectedType;
                }
            }
        }


        return DataType.UNKNOWN;
    }

    @SneakyThrows
    private static String formatDate(OdfTextDocument document, String styleName, String raw) {
        if (StringUtils.isBlank(raw)) return "";

        // Einfachere Logik: wir akzeptieren gängige Eingabeformate und liefern immer "yyyy-MM-dd" zurück.
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
            // Fallback: unverändertes Raw zurückgeben
            return raw;
        }

        DateTimeFormatter outFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withLocale(Locale.getDefault());

        try {
            if (parsed.query(TemporalQueries.localDate()) != null) {
                LocalDate ld = LocalDate.from(parsed);
                return outFmt.format(ld);
            } else if (parsed.query(TemporalQueries.localDate()) == null && parsed.query(TemporalQueries.localTime()) != null) {
                // Nur Zeit enthalten — kein Datum ableitbar, Raw zurückgeben
                return raw;
            } else if (parsed.query(TemporalQueries.offset()) != null) {
                // OffsetDateTime -> Datumsteil extrahieren
                LocalDate ld = OffsetDateTime.from(parsed).toLocalDate();
                return outFmt.format(ld);
            } else {
                return raw;
            }
        } catch (Exception e) {
            return raw;
        }
    }

    private static String formatNumber(OdfTextDocument document, String style, String value) {
        if (StringUtils.isBlank(style)) {
            return value;
        }
        Double numericValue = null;
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
        DecimalFormat df = findDecimalFormatForStyle(document, style);

        return df.format(numericValue);
    }

    // -------- Hilfs-Methoden: number-style Suche und Patternerzeugung (vereinfachte Heuristik) --------
    @SneakyThrows
    private static DecimalFormat findDecimalFormatForStyle(OdfTextDocument document, String styleName) {
        OdfContentDom contentDom = document.getContentDom();
        Document stylesDom = document.getStylesDom();

        String[] styleElements = {"number:number-style", "number:percentage-style", "number:currency-style"};
        Map<String, NumberStyle> styleNodes = new HashMap<>();

        for (String style : styleElements) {
            NodeList nl = contentDom.getElementsByTagName(style);
            for (int i = 0; i < nl.getLength(); i++) {
                var item = (Element) nl.item(i);
                styleNodes.put(item.getAttribute("style:name"), createNumberStyle(item));
            }
            nl = stylesDom.getElementsByTagName(style);
            for (int i = 0; i < nl.getLength(); i++) {
                var item = (Element) nl.item(i);
                // only add if absent to let content.xml override styles.xml when names collide
                styleNodes.putIfAbsent(item.getAttribute("style:name"), createNumberStyle(item));
            }
        }

        return buildDecimalFormatFromNumberStyleElement(styleNodes.get(styleName));
    }

    private static NumberStyle createNumberStyle(Element elem) {
        String country = StringUtils.defaultIfBlank(elem.getAttribute("number:country"), "DE");
        String language = StringUtils.defaultIfBlank(elem.getAttribute("number:language"), "de");
        int decimalPlaces = 0;
        int minimalDecimalPlaces = 0;
        int minIntegerDigits = 1;
        boolean grouping = false;
        String symbol = "";

        var childs = elem.getChildNodes();
        for (int i = 0; i < childs.getLength(); i++) {
            Element child = (Element) childs.item(i);
            if ("number:number".equals(child.getTagName())) {
                decimalPlaces = Integer.parseInt(child.getAttribute("number:decimal-places"));
                minimalDecimalPlaces = Integer.parseInt(child.getAttribute("number:min-decimal-places"));
                minIntegerDigits = Integer.parseInt(child.getAttribute("number:min-integer-digits"));
                grouping = Boolean.parseBoolean(child.getAttribute("number:grouping"));
            }
            if ("number:text".equals(child.getTagName())) {
                symbol = child.getTextContent();
            }
            if ("number:currency-symbol".equals(child.getTagName())) {
                symbol = " " + child.getTextContent();
            }
        }
        return new NumberStyle(minIntegerDigits, decimalPlaces, minimalDecimalPlaces, grouping, symbol, country, language);
    }


    private static DecimalFormat buildDecimalFormatFromNumberStyleElement(NumberStyle style) {
        DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(style.getLocale());
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
     * - `country`: Indicates the country code used for locale-specific formatting.
     * - `language`: Specifies the language code used for locale-specific formatting.
     */
    record NumberStyle(
            int minIntegerDigits,
            int decimalPlaces,
            int minDecimalPlaces,
            boolean grouping,
            String symbol,
            String country,
            String language
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

        /**
         * Retrieves a locale instance based on the `language` and `country` fields of the containing record.
         *
         * @return a {@code Locale} object constructed from the `language` and `country` values.
         */
        public Locale getLocale() {
            return Locale.of(language, country);
        }

    }
}
