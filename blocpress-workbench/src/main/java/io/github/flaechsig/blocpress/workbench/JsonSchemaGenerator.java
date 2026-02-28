package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates JSON-Schema from extracted template fields and repetition groups.
 *
 * Converts dot-notation field names into nested JSON-Schema properties:
 * - "customer.name" → { properties: { customer: { properties: { name: { type: "string" } } } } }
 * - Automatically detects arrays from repetition groups
 * - Handles nested objects
 */
@ApplicationScoped
public class JsonSchemaGenerator {

    @Inject
    ObjectMapper objectMapper;

    /**
     * Generate JSON-Schema from template fields and repetition groups.
     *
     * @param fieldNames List of field names in dot-notation (e.g., "customer.name", "items")
     * @param arrayPaths List of array paths (e.g., "items", "customer.addresses")
     * @return JSON-Schema as JsonNode
     */
    public JsonNode generateSchema(List<String> fieldNames, List<String> arrayPaths) {
        Set<String> uniqueFields = new HashSet<>(fieldNames);
        Set<String> arrayPathsSet = new HashSet<>(arrayPaths);

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();
        schema.set("properties", properties);

        // Build nested structure from dot-notation fields
        for (String fieldName : uniqueFields) {
            String[] parts = fieldName.split("\\.");
            addPropertyToSchema(properties, parts, 0, arrayPathsSet);
        }

        return schema;
    }

    /**
     * Recursively add property to schema, handling nested objects and arrays.
     */
    private void addPropertyToSchema(ObjectNode parentProperties, String[] parts, int depth, Set<String> arrayPaths) {
        if (depth >= parts.length) {
            return;
        }

        String partName = parts[depth];
        String fullPath = buildPath(parts, depth);

        // Check if this is the last part
        if (depth == parts.length - 1) {
            // Leaf property
            var existingProp = (ObjectNode) parentProperties.get(partName);

            ObjectNode prop;

            // Check if this path is an array
            if (arrayPaths.contains(fullPath)) {
                // Create array of objects
                if (existingProp != null && "array".equals(existingProp.get("type").asText())) {
                    // Array already exists, don't overwrite
                    prop = existingProp;
                } else {
                    prop = objectMapper.createObjectNode();
                    prop.put("type", "array");

                    ObjectNode items = objectMapper.createObjectNode();
                    items.put("type", "object");
                    prop.set("items", items);

                    parentProperties.set(partName, prop);
                }
            } else {
                // Infer type from field name (heuristic)
                if (existingProp == null) {
                    prop = objectMapper.createObjectNode();
                    String type = inferType(partName);
                    prop.put("type", type);
                    parentProperties.set(partName, prop);
                } else {
                    // Property already exists, don't overwrite
                    prop = existingProp;
                }
            }
        } else {
            // Intermediate property - create nested object
            // Check if property already exists
            var existingProp = (ObjectNode) parentProperties.get(partName);

            // Check if this path is an array
            if (arrayPaths.contains(fullPath)) {
                // Create array of objects
                ObjectNode prop;
                ObjectNode itemProperties;

                if (existingProp != null && "array".equals(existingProp.get("type").asText())) {
                    // Array already exists, reuse it
                    prop = existingProp;
                    itemProperties = (ObjectNode) prop.get("items").get("properties");
                } else {
                    // Create new array
                    prop = objectMapper.createObjectNode();
                    prop.put("type", "array");

                    ObjectNode items = objectMapper.createObjectNode();
                    items.put("type", "object");

                    itemProperties = objectMapper.createObjectNode();
                    items.set("properties", itemProperties);

                    prop.set("items", items);
                    parentProperties.set(partName, prop);
                }

                // Add remaining parts as properties of array items
                addPropertyToSchema(itemProperties, parts, depth + 1, arrayPaths);
            } else {
                // Regular nested object
                ObjectNode prop;
                ObjectNode nestedProperties;

                if (existingProp != null && "object".equals(existingProp.get("type").asText())) {
                    // Object already exists, reuse it
                    prop = existingProp;
                    nestedProperties = (ObjectNode) prop.get("properties");
                } else {
                    // Create new object
                    prop = objectMapper.createObjectNode();
                    prop.put("type", "object");

                    nestedProperties = objectMapper.createObjectNode();
                    prop.set("properties", nestedProperties);

                    parentProperties.set(partName, prop);
                }

                // Recursively add remaining parts
                addPropertyToSchema(nestedProperties, parts, depth + 1, arrayPaths);
            }
        }
    }

    /**
     * Build the full path up to a given depth (e.g., "customer.address" at depth 1 from ["customer", "address", "street"]).
     */
    private String buildPath(String[] parts, int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= depth; i++) {
            if (i > 0) {
                sb.append(".");
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /**
     * Infer JSON type from field name (heuristic - conservative approach).
     * Defaults to "string" for safety. Only recognizes clear numeric/boolean patterns.
     *
     * Examples:
     * - "amount", "price", "total" → "number"
     * - "active", "enabled", "deleted" → "boolean"
     * - "number", "id", "code" → "string" (ambiguous, safe default)
     * - everything else → "string"
     */
    private String inferType(String fieldName) {
        String lower = fieldName.toLowerCase();

        // Numeric type keywords - CONSERVATIVE: only clear cases
        // Excluded: "number", "count" (often text identifiers like invoice.number)
        if (lower.contains("price") || lower.contains("amount") || lower.contains("quantity") ||
            lower.contains("total") || lower.contains("rate") || lower.contains("discount") ||
            lower.contains("tax") || lower.contains("fee") || lower.contains("cost") ||
            lower.contains("salary") || lower.contains("paymentTerms") ||
            // Time/Duration (clear numeric context)
            lower.contains("daysTerms") || lower.contains("daysTerm") ||
            lower.contains("percentage") || lower.contains("percent") ||
            // Measurements
            lower.contains("weight") || lower.contains("height") || lower.contains("width") ||
            lower.contains("depth") || lower.contains("volume") || lower.contains("area") ||
            lower.contains("temperature") ||
            // Accounting/Finance
            lower.contains("netTotal") || lower.contains("grossTotal") || lower.contains("subtotal") ||
            lower.contains("unitPrice") || lower.contains("listPrice")) {
            return "number";
        }

        // Boolean type keywords
        if (lower.contains("active") || lower.contains("enabled") || lower.contains("deleted") ||
            lower.contains("flag") || lower.contains("checked") || lower.contains("is") ||
            lower.contains("has") || lower.contains("success") || lower.contains("valid") ||
            lower.contains("approved") || lower.contains("confirmed") || lower.contains("required")) {
            return "boolean";
        }

        // DEFAULT: string (conservative)
        // This includes: number, id, code, identifier, reference, name, etc.
        return "string";
    }
}
