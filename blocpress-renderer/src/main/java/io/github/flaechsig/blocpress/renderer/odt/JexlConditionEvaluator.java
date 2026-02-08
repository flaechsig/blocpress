package io.github.flaechsig.blocpress.renderer.odt;

import org.apache.commons.jexl3.*;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

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

    /**
     * Kleine Vorverarbeitung:
     * - Entferne "ooow:" Prefix (häufig in ODT-Conditions)
     * - Ent-escape HTML-Anführungszeichen (&quot;) durch echte Anführungszeichen
     * - Normalisiere logische Operatoren (AND/OR/NOT -> &&/||/!)
     * - Konvertiere einfaches '=' in '==' (token-sicher)
     */
    private static String preprocessCondition(String raw) {
        String s = raw;

        s = s.replaceAll("(?<![\\w.])ooow:", "");
        s = s.replace("&quot;", "\"");
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