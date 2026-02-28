package io.github.flaechsig.blocpress.workbench;

import io.github.flaechsig.blocpress.core.TemplateDocument;
import io.github.flaechsig.blocpress.core.TemplateElement;
import io.github.flaechsig.blocpress.core.odt.JexlConditionEvaluator;
import io.github.flaechsig.blocpress.core.odt.OdtTemplateElement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates ODT templates and generates JSON-Schema for their data structure.
 *
 * Process:
 * 1. Load ODT and extract user fields (dot-notation names)
 * 2. Identify repetition groups (arrays)
 * 3. Generate JSON-Schema from fields + arrays
 * 4. Validate field names and JEXL conditions
 * 5. Return ValidationResult with schema
 */
@ApplicationScoped
public class TemplateValidator {

    private static final Pattern VALID_FIELD_NAME =
        Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$");

    @Inject
    ObjectMapper objectMapper;

    @Inject
    JsonSchemaGenerator schemaGenerator;

    public ValidationResult validate(byte[] templateContent) {
        List<ValidationResult.ValidationMessage> errors = new ArrayList<>();
        List<ValidationResult.ValidationMessage> warnings = new ArrayList<>();
        List<String> fieldNames = new ArrayList<>();
        List<String> arrayPaths = new ArrayList<>();
        Map<String, String> fieldValues = new HashMap<>();  // fieldName -> text content value
        JsonNode schema = null;

        Path tempFile = null;
        try {
            // Step 1: Create temporary file and load ODT document
            tempFile = Files.createTempFile("template-validation-", ".odt");
            Files.write(tempFile, templateContent);
            URL templateUrl = tempFile.toUri().toURL();

            TemplateDocument doc = TemplateDocument.load(templateUrl);

            // Step 2: Extract and validate user fields
            List<TemplateElement> extractedFields = doc.collectUserFields();
            Set<String> uniqueFields = new HashSet<>();

            for (TemplateElement field : extractedFields) {
                String name = field.getName();
                uniqueFields.add(name);

                // Extract field value (text content from ODT element)
                String fieldValue = extractFieldValue(field);
                if (fieldValue != null && !fieldValue.isBlank()) {
                    fieldValues.put(name, fieldValue);
                }

                // Check field name format
                if (!VALID_FIELD_NAME.matcher(name).matches()) {
                    warnings.add(new ValidationResult.ValidationMessage(
                        "INVALID_FIELD_NAME",
                        "Field '" + name + "' does not follow dot-notation pattern"
                    ));
                }
            }
            fieldNames.addAll(uniqueFields);

            // Step 3: Identify repetition groups (loops)
            // Note: doc.findRepeatGroups() uses emptyData and won't detect arrays from template structure alone.
            // Use conservative heuristic: only recognize known array field names with multiple fields.

            Set<String> knownArrayNames = Set.of(
                // Standard plurals
                "positions", "items", "lines", "rows", "details", "entries",
                // Business domain specific
                "products", "articles", "lineItems", "orderItems", "invoiceItems",
                "addresses", "payments", "documents", "attachments",
                "shipments", "packages", "expenses", "transactions",
                "members", "participants", "attendees", "employees",
                "permissions", "roles", "connections", "relations"
            );

            Set<String> fieldPrefixes = new HashSet<>();
            for (String fieldName : fieldNames) {
                if (fieldName.contains(".")) {
                    int lastDotIndex = fieldName.lastIndexOf(".");
                    String prefix = fieldName.substring(0, lastDotIndex);
                    fieldPrefixes.add(prefix);
                }
            }

            // Count how many fields each prefix has and check against known array names
            Set<String> detectedArrayPaths = new HashSet<>();
            for (String prefix : fieldPrefixes) {
                int count = 0;
                for (String fieldName : fieldNames) {
                    if (fieldName.startsWith(prefix + ".")) {
                        count++;
                    }
                }
                // Only treat as array if it's a known array name AND has 2+ fields
                if (knownArrayNames.contains(prefix) && count >= 2) {
                    detectedArrayPaths.add(prefix);
                }
            }

            arrayPaths.addAll(detectedArrayPaths);

            System.out.println("DEBUG: Found array paths (heuristic): " + arrayPaths);
            System.out.println("DEBUG: Field names: " + fieldNames);

            // Step 4: Generate JSON-Schema from fields and arrays
            schema = schemaGenerator.generateSchema(fieldNames, arrayPaths, fieldValues);
            System.out.println("DEBUG: Generated schema: " + schema);

            // Step 5: Extract and validate conditions (syntax only)
            List<TemplateElement> conditionalElements = doc.collectConditionalTemplateElements();
            for (TemplateElement element : conditionalElements) {
                String elementName = element.getName();
                boolean syntaxValid = true;
                String errorMessage = null;

                // Try to validate condition syntax
                try {
                    JexlConditionEvaluator.evaluate(elementName);
                } catch (IllegalArgumentException e) {
                    syntaxValid = false;
                    errorMessage = e.getMessage();
                    errors.add(new ValidationResult.ValidationMessage(
                        "INVALID_CONDITION",
                        "Condition syntax error: " + e.getMessage()
                    ));
                } catch (Exception e) {
                    // Ignore other exceptions (they might be context-related)
                }

                if (!syntaxValid) {
                    warnings.add(new ValidationResult.ValidationMessage(
                        "INVALID_CONDITION_SYNTAX",
                        "Condition '" + elementName + "' has syntax error: " + errorMessage
                    ));
                }
            }

        } catch (Exception e) {
            errors.add(new ValidationResult.ValidationMessage(
                "INVALID_ODT_STRUCTURE",
                "Could not load ODT file: " + e.getMessage()
            ));
        } finally {
            // Clean up temporary file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    // Log but don't fail validation if cleanup fails
                }
            }
        }

        boolean isValid = errors.isEmpty();

        // If schema is null due to error, create empty object schema
        if (schema == null) {
            ObjectNode emptySchema = objectMapper.createObjectNode();
            emptySchema.put("type", "object");
            emptySchema.set("properties", objectMapper.createObjectNode());
            schema = emptySchema;
        }

        return new ValidationResult(
            isValid,
            schema,
            errors,
            warnings
        );
    }

    /**
     * Extracts the text content value of a user field from the ODT element.
     * This represents the displayed/default value of the field.
     *
     * @param field the template element representing a user field
     * @return the text content of the field, or null if not available
     */
    private String extractFieldValue(TemplateElement field) {
        try {
            // Cast to OdtTemplateElement to access the text content
            if (field instanceof OdtTemplateElement odtElement) {
                return odtElement.getTextContent();
            }
        } catch (Exception e) {
            // Log but don't fail if we can't extract the value
            System.out.println("DEBUG: Could not extract field value: " + e.getMessage());
        }
        return null;
    }
}
