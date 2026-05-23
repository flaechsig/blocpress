package io.github.flaechsig.blocpress.core.odt;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.jexl3.*;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hilfsklasse zur Auswertung von ODF/Writer-Bedingungen mittels Apache Commons JEXL.
 *
 * - Nimmt den kompletten Condition-Text (z. B. "ooow:kunde.anrede == \"FRAU\" OR ...")
 * - Führt leichte Vorverarbeitung durch (Entfernen von ooow:-Prefix, Ent-escape von &quot;, Normalisierung von AND/OR/NOT, = -> ==)
 * - Baut aus der flachen data-Map eine verschachtelte Map (dot-Notation -> Map-Struktur),
 *   damit Ausdrücke wie "kunde.anrede" in JEXL zugreifbar sind.
 * - Evaluiert die Bedingung in einem Map-Kontext und liefert boolean zurück.
 *
 * Verwendung:
 *   boolean result = JexlConditionEvaluator.evaluate(conditionText, dataMap);
 *
 * <p><b>Design-Referenzen:</b></p>
 * <ul>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-tf-5">TF-5: Dokument generieren</a> (Schritt 8: IF-Bedingungen evaluieren)</li>
 * </ul>
 */
public final class JexlConditionEvaluator {
    private static final JexlEngine JEXL = new JexlBuilder()
            .silent(false)
            .strict(false)
            .create();

    private JexlConditionEvaluator() {
        // Utility-Klasse
    }

    /**
     * Evaluiert die übergebene Bedingung gegen die übergebenen Daten.
     *
     * @param conditionRaw der rohe Condition-Text aus dem Dokument
     * @return true, wenn Bedingung als wahr ausgewertet wird, false sonst
     * @throws IllegalArgumentException bei fehlerhafter Syntax oder Auswertungsfehlern
     */
    public static boolean evaluate(String conditionRaw) {
        if (StringUtils.isBlank(conditionRaw)) {
            return true; // leere Bedingung => sichtbar
        }

        String expr = preprocessCondition(conditionRaw);

        JexlContext ctx = new MapContext();
        JexlExpression jexlExpr;
        try {
            jexlExpr = JEXL.createExpression(expr);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Ungültige Bedingung: " + conditionRaw, ex);
        }

        Object result;
        try {
            result = jexlExpr.evaluate(ctx);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Fehler bei der Auswertung der Bedingung: " + conditionRaw, ex);
        }

        return toBooleanResult(result);
    }

    /**
     * Evaluiert die übergebene Bedingung gegen den übergebenen JsonNode-Kontext.
     *
     * @param conditionRaw der rohe Condition-Text aus dem Dokument
     * @param data         JsonNode mit Testdaten (wird als Flat-Map in JEXL-Kontext überführt)
     * @return true, wenn Bedingung als wahr ausgewertet wird, false sonst
     */
    public static boolean evaluate(String conditionRaw, JsonNode data) {
        if (StringUtils.isBlank(conditionRaw)) {
            return true;
        }

        String expr = preprocessCondition(conditionRaw);

        // Build JEXL context from flattened JSON (dot-notation paths → nested maps for JEXL access)
        Map<String, Object> flatMap = new HashMap<>();
        if (data != null) {
            flattenJson(data, "", flatMap);
        }
        // Build nested map structure so JEXL can resolve "kunde.name" as kunde["name"]
        Map<String, Object> nestedMap = buildNestedMap(flatMap);

        JexlContext ctx = new MapContext(nestedMap);
        JexlExpression jexlExpr;
        try {
            jexlExpr = JEXL.createExpression(expr);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Ungültige Bedingung: " + conditionRaw, ex);
        }

        Object result;
        try {
            result = jexlExpr.evaluate(ctx);
        } catch (Exception ex) {
            return false; // Missing field → condition not applicable → false
        }

        return toBooleanResult(result);
    }

    private static void flattenJson(JsonNode node, String prefix, Map<String, Object> result) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                flattenJson(entry.getValue(), key, result);
            }
        } else if (node.isArray()) {
            result.put(prefix + ".length", node.size());
            for (int i = 0; i < node.size(); i++) {
                flattenJson(node.get(i), prefix + "." + i, result);
            }
        } else if (node.isBoolean()) {
            result.put(prefix, node.booleanValue());
        } else if (node.isNumber()) {
            result.put(prefix, node.numberValue());
        } else if (node.isNull()) {
            result.put(prefix, null);
        } else {
            result.put(prefix, node.asText());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildNestedMap(Map<String, Object> flatMap) {
        Map<String, Object> root = new HashMap<>();
        for (Map.Entry<String, Object> entry : flatMap.entrySet()) {
            String[] parts = entry.getKey().split("\\.");
            Map<String, Object> current = root;
            for (int i = 0; i < parts.length - 1; i++) {
                current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new HashMap<String, Object>());
            }
            current.put(parts[parts.length - 1], entry.getValue());
        }
        return root;
    }

    private static boolean toBooleanResult(Object result) {
        if (result instanceof Boolean b) {
            return b;
        }
        if (result == null) {
            return false;
        }
        if (result instanceof Number n) {
            return n.doubleValue() != 0.0;
        }
        String s = result.toString().trim();
        if (s.isEmpty()) {
            return false;
        }
        if ("TRUE".equalsIgnoreCase(s) || "1".equals(s)) {
            return true;
        }
        if ("FALSE".equalsIgnoreCase(s) || "0".equals(s)) {
            return false;
        }
        // Default: non-empty string -> true
        return true;
    }

    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");

    /**
     * Kleine Vorverarbeitung:
     * - Entferne "ooow:" Prefix (häufig in ODT-Conditions)
     * - Ent-escape HTML-Anführungszeichen (&quot;) durch echte Anführungszeichen
     * - Normalisiere logische Operatoren (AND/OR/NOT -> &&/||/!)
     * - Konvertiere einfaches '=' in '==' (token-sicher)
     *
     * Keyword-Replacements werden nur ausserhalb von String-Literalen angewendet.
     */
    private static String preprocessCondition(String raw) {
        String s = raw;
        s = s.replaceAll("(?<![\\w.])ooow:", "");
        s = s.replace("&quot;", "\"");

        Matcher m = STRING_LITERAL.matcher(s);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(replaceKeywords(s.substring(last, m.start())));
            out.append(m.group());
            last = m.end();
        }
        out.append(replaceKeywords(s.substring(last)));
        return out.toString();
    }

    private static String replaceKeywords(String s) {
        s = s.replaceAll("(?i)(?<![\\w.])AND(?![\\w.])", "&&");
        s = s.replaceAll("(?i)(?<![\\w.])OR(?![\\w.])", "||");
        s = s.replaceAll("(?i)(?<![\\w.])NOT(?![\\w.])", "!");
        s = s.replaceAll("(?i)(?<![\\w.])EQ(?![\\w.])", "==");
        s = s.replaceAll("(?i)(?<![\\w.])NEQ(?![\\w.])", "!=");
        s = s.replaceAll("(?i)(?<![\\w.])TRUE(?![\\w.])", "true");
        s = s.replaceAll("(?i)(?<![\\w.])FALSE(?![\\w.])", "false");
        s = s.replaceAll("(?i)(?<![\\w.])<>(?![\\w.])", "!=");
        s = s.replaceAll("(?<![!<>=])=(?![=])", "==");
        return s;
    }

}