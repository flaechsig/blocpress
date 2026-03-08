package io.github.flaechsig.blocpress.workbench.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.flaechsig.blocpress.core.odt.JexlConditionEvaluator;
import io.github.flaechsig.blocpress.workbench.entity.Template;
import io.github.flaechsig.blocpress.workbench.entity.TestDataSet;
import io.github.flaechsig.blocpress.workbench.entity.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Analyses which template paths (fields, conditions, repetition groups) are
 * covered by the existing TestDataSets and generates suggestions for missing cases.
 */
@ApplicationScoped
public class CoverageAnalysisService {

    @Inject
    ObjectMapper objectMapper;

    // ===== Public API =====

    public CoverageReport analyze(UUID templateId) {
        Template template = Template.findById(templateId);
        if (template == null || template.validationResult == null) {
            return new CoverageReport(0,
                new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), List.of());
        }

        ValidationResult vr = template.validationResult;
        List<TestDataSet> testDataSets = TestDataSet.list("template.id", templateId);

        // Collect paths from schema
        List<String> fieldPaths = extractLeafPaths(vr.schema());
        List<String> arrayPaths = vr.repetitionGroups() != null ? vr.repetitionGroups() : List.of();
        List<String> conditions = vr.conditions() != null ? vr.conditions() : List.of();

        // Initialize coverage tracking
        Map<String, Boolean> fieldCovered = new LinkedHashMap<>();
        fieldPaths.forEach(f -> fieldCovered.put(f, false));

        // [trueCase, falseCase]
        Map<String, boolean[]> conditionCoverage = new LinkedHashMap<>();
        conditions.forEach(c -> conditionCoverage.put(c, new boolean[]{false, false}));

        // [zero items, one item, two+ items]
        Map<String, boolean[]> groupCoverage = new LinkedHashMap<>();
        arrayPaths.forEach(p -> groupCoverage.put(p, new boolean[]{false, false, false}));

        // Evaluate each test data set
        for (TestDataSet tds : testDataSets) {
            JsonNode data = tds.testData;
            if (data == null) continue;

            for (String path : fieldPaths) {
                if (!fieldCovered.get(path) && isPresent(data, path)) {
                    fieldCovered.put(path, true);
                }
            }

            for (String expr : conditions) {
                boolean[] cov = conditionCoverage.get(expr);
                try {
                    boolean result = JexlConditionEvaluator.evaluate(expr, data);
                    if (result) cov[0] = true;
                    else cov[1] = true;
                } catch (Exception ignored) {
                }
            }

            for (String path : arrayPaths) {
                int count = getArrayCount(data, path);
                boolean[] cov = groupCoverage.get(path);
                if (count == 0) cov[0] = true;
                else if (count == 1) cov[1] = true;
                else cov[2] = true;
            }
        }

        // Compute coverage percentage
        int total = fieldPaths.size()
            + conditions.size() * 2
            + arrayPaths.size() * 3;
        int covered = (int) fieldCovered.values().stream().filter(Boolean::booleanValue).count()
            + conditionCoverage.values().stream().mapToInt(a -> (a[0] ? 1 : 0) + (a[1] ? 1 : 0)).sum()
            + groupCoverage.values().stream().mapToInt(a -> (a[0] ? 1 : 0) + (a[1] ? 1 : 0) + (a[2] ? 1 : 0)).sum();
        int percent = total == 0 ? 100 : covered * 100 / total;

        List<TestDataSuggestion> suggestions = buildSuggestions(conditionCoverage, groupCoverage, testDataSets);

        return new CoverageReport(percent, fieldCovered, conditionCoverage, groupCoverage, suggestions);
    }

    // ===== Coverage data records =====

    public record CoverageReport(
        int coveragePercent,
        Map<String, Boolean> fieldCoverage,
        Map<String, boolean[]> conditionCoverage,
        Map<String, boolean[]> repetitionGroupCoverage,
        List<TestDataSuggestion> suggestions
    ) {}

    public record TestDataSuggestion(
        String reason,
        String suggestedName,
        JsonNode suggestedData
    ) {}

    // ===== Private helpers =====

    /**
     * Extracts all leaf paths from a JSON-Schema (dot-notation).
     * Arrays are represented by their item properties (e.g. "items.description").
     */
    private List<String> extractLeafPaths(JsonNode schema) {
        List<String> result = new ArrayList<>();
        if (schema == null) return result;
        collectLeafPaths(schema, "", result);
        return result;
    }

    private void collectLeafPaths(JsonNode node, String prefix, List<String> result) {
        if (node == null) return;
        JsonNode type = node.get("type");
        if (type == null) return;

        String typeStr = type.asText();
        if ("object".equals(typeStr)) {
            JsonNode props = node.get("properties");
            if (props != null) {
                Iterator<Map.Entry<String, JsonNode>> fields = props.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String childPath = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                    collectLeafPaths(entry.getValue(), childPath, result);
                }
            }
        } else if ("array".equals(typeStr)) {
            JsonNode items = node.get("items");
            if (items != null) {
                collectLeafPaths(items, prefix, result);
            }
        } else {
            // leaf
            if (!prefix.isEmpty()) {
                result.add(prefix);
            }
        }
    }

    /**
     * Returns true when the JsonNode contains a non-null, non-empty value at the given dot-path.
     */
    private boolean isPresent(JsonNode data, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = data;
        for (String part : parts) {
            if (current == null || current.isMissingNode()) return false;
            if (current.isArray()) {
                // descend into first array element
                if (current.isEmpty()) return false;
                current = current.get(0);
                if (current == null) return false;
            }
            current = current.get(part);
        }
        if (current == null || current.isNull() || current.isMissingNode()) return false;
        if (current.isTextual() && current.asText().isBlank()) return false;
        return true;
    }

    /**
     * Returns the array length at the given dot-path in the JsonNode.
     */
    private int getArrayCount(JsonNode data, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = data;
        for (String part : parts) {
            if (current == null || current.isMissingNode()) return 0;
            current = current.get(part);
        }
        if (current == null || !current.isArray()) return 0;
        return current.size();
    }

    /**
     * Builds a list of TestDataSuggestions for uncovered cases.
     * Clones the first existing TestDataSet and modifies the relevant field.
     */
    private List<TestDataSuggestion> buildSuggestions(
        Map<String, boolean[]> conditionCoverage,
        Map<String, boolean[]> groupCoverage,
        List<TestDataSet> testDataSets
    ) {
        List<TestDataSuggestion> suggestions = new ArrayList<>();
        JsonNode baseData = testDataSets.isEmpty() ? objectMapper.createObjectNode()
            : testDataSets.get(0).testData;

        for (Map.Entry<String, boolean[]> entry : conditionCoverage.entrySet()) {
            String expr = entry.getKey();
            boolean[] cov = entry.getValue();

            if (!cov[0]) {
                // true-case missing: try to extract the expected value from the expression
                String suggestedName = "Auto: " + abbreviate(expr) + " (true-Fall)";
                JsonNode suggested = cloneAndPatch(baseData, expr, true);
                suggestions.add(new TestDataSuggestion(
                    "Bedingung '" + expr + "' — true-Fall fehlt", suggestedName, suggested));
            }
            if (!cov[1]) {
                String suggestedName = "Auto: " + abbreviate(expr) + " (false-Fall)";
                JsonNode suggested = cloneAndPatch(baseData, expr, false);
                suggestions.add(new TestDataSuggestion(
                    "Bedingung '" + expr + "' — false-Fall fehlt", suggestedName, suggested));
            }
        }

        for (Map.Entry<String, boolean[]> entry : groupCoverage.entrySet()) {
            String path = entry.getKey();
            boolean[] cov = entry.getValue();

            if (!cov[0]) {
                suggestions.add(new TestDataSuggestion(
                    "Wiederholungsgruppe '" + path + "' — 0-Elemente-Fall fehlt",
                    "Auto: " + path + " leer",
                    cloneAndSetArray(baseData, path, 0)));
            }
            if (!cov[1]) {
                suggestions.add(new TestDataSuggestion(
                    "Wiederholungsgruppe '" + path + "' — 1-Element-Fall fehlt",
                    "Auto: " + path + " (1 Element)",
                    cloneAndSetArray(baseData, path, 1)));
            }
            if (!cov[2]) {
                suggestions.add(new TestDataSuggestion(
                    "Wiederholungsgruppe '" + path + "' — 2+-Elemente-Fall fehlt",
                    "Auto: " + path + " (2 Elemente)",
                    cloneAndSetArray(baseData, path, 2)));
            }
        }

        return suggestions;
    }

    private String abbreviate(String expr) {
        return expr.length() > 40 ? expr.substring(0, 40) + "…" : expr;
    }

    /**
     * Creates a deep copy of baseData and tries to set the field referenced in the
     * condition expression to a value that satisfies (true) or not satisfies (false) the condition.
     */
    private JsonNode cloneAndPatch(JsonNode baseData, String expr, boolean targetResult) {
        ObjectNode clone = baseData.deepCopy() instanceof ObjectNode on ? on : objectMapper.createObjectNode();

        // Try to extract "path == VALUE" pattern
        String processed = expr.replaceAll("(?i)ooow:", "").replace("&quot;", "\"").trim();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("([a-zA-Z][a-zA-Z0-9_.]+)\\s*==\\s*[\"']?([^\"'\\s)]+)[\"']?")
            .matcher(processed);

        if (m.find()) {
            String fieldPath = m.group(1);
            String expectedValue = m.group(2);
            String valueToSet = targetResult ? expectedValue : "";
            setNestedValue(clone, fieldPath, objectMapper.getNodeFactory().textNode(valueToSet));
        }

        return clone;
    }

    /**
     * Creates a deep copy of baseData and resizes the array at path to targetCount elements.
     */
    private JsonNode cloneAndSetArray(JsonNode baseData, String path, int targetCount) {
        ObjectNode clone = baseData.deepCopy() instanceof ObjectNode on ? on : objectMapper.createObjectNode();
        JsonNode existing = getNestedNode(clone, path);

        com.fasterxml.jackson.databind.node.ArrayNode arr = objectMapper.createArrayNode();
        if (targetCount > 0 && existing != null && existing.isArray() && !existing.isEmpty()) {
            JsonNode firstItem = existing.get(0);
            for (int i = 0; i < targetCount; i++) {
                arr.add(firstItem.deepCopy());
            }
        }
        setNestedValue(clone, path, arr);
        return clone;
    }

    private JsonNode getNestedNode(JsonNode root, String path) {
        String[] parts = path.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null || !current.has(part)) return null;
            current = current.get(part);
        }
        return current;
    }

    private void setNestedValue(ObjectNode root, String path, JsonNode value) {
        String[] parts = path.split("\\.");
        ObjectNode current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonNode child = current.get(parts[i]);
            if (child == null || !child.isObject()) {
                ObjectNode newChild = objectMapper.createObjectNode();
                current.set(parts[i], newChild);
                current = newChild;
            } else {
                current = (ObjectNode) child;
            }
        }
        current.set(parts[parts.length - 1], value);
    }
}
