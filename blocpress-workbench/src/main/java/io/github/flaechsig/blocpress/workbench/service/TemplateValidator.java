package io.github.flaechsig.blocpress.workbench.service;

import io.github.flaechsig.blocpress.core.TemplateDocument;
import io.github.flaechsig.blocpress.core.TemplateElement;
import io.github.flaechsig.blocpress.core.odt.JexlConditionEvaluator;
import io.github.flaechsig.blocpress.core.odt.OdtTemplateElement;
import io.github.flaechsig.blocpress.workbench.entity.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    private static final Logger LOG = Logger.getLogger(TemplateValidator.class);

    private static final Pattern VALID_FIELD_NAME =
        Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$");

    /** Matches dot-notation paths inside condition expressions, e.g. customer.gender in 'customer.gender == "FEMALE"' */
    private static final Pattern FIELD_PATH_IN_CONDITION =
        Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+");

    @Inject
    public ObjectMapper objectMapper;

    @Inject
    public JsonSchemaGenerator schemaGenerator;

    public ValidationResult validate(byte[] templateContent) {
        List<ValidationResult.ValidationMessage> errors = new ArrayList<>();
        List<ValidationResult.ValidationMessage> warnings = new ArrayList<>();
        List<String> fieldNames = new ArrayList<>();
        List<String> arrayPaths = new ArrayList<>();
        Map<String, String> fieldValues = new HashMap<>();  // fieldName -> text content value
        Map<String, String> fieldTypes = new HashMap<>();   // fieldName -> JSON schema type
        Set<String> conditionExpressions = new LinkedHashSet<>();
        Set<String> detectedArrayPaths = new HashSet<>();
        JsonNode schema = null;

        Path tempFile = null;
        try {
            // Step 1: Create temporary file and load ODT document
            tempFile = Files.createTempFile("template-validation-", ".odt");
            Files.write(tempFile, templateContent);
            URL templateUrl = tempFile.toUri().toURL();

            TemplateDocument doc = TemplateDocument.load(templateUrl);

            // Step 2: Extract and validate user fields.
            // Prefer text:user-field-decl declarations — they list ALL fields including those
            // only referenced in conditions (e.g. customer.gender used in Conditional Text).
            // Fall back to text:user-field-get usages for templates without explicit declarations.
            List<TemplateElement> extractedFields;
            if (doc instanceof io.github.flaechsig.blocpress.core.odt.OdtTemplateDocument odtDocForDecl) {
                extractedFields = odtDocForDecl.collectUserFieldDeclarations();
                if (extractedFields.isEmpty()) {
                    extractedFields = doc.collectUserFields();
                }
            } else {
                extractedFields = doc.collectUserFields();
            }
            Set<String> uniqueFields = new HashSet<>();

            for (TemplateElement field : extractedFields) {
                String name = field.getName();
                uniqueFields.add(name);

                // Extract field value (text content from ODT element)
                String fieldValue = extractFieldValue(field);
                if (fieldValue != null && !fieldValue.isBlank()) {
                    fieldValues.put(name, fieldValue);
                }

                // Extract field type from ODT value-type attribute
                String fieldType = extractFieldType(field);
                if (fieldType != null) {
                    fieldTypes.put(name, fieldType);
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

            // Step 3: Identify repetition groups via structural detection.
            // Scan text:section and table:table-row elements: if all user fields within an element
            // share the same top-level prefix, that prefix is an array path.
            if (doc instanceof io.github.flaechsig.blocpress.core.odt.OdtTemplateDocument odtDoc) {
                detectedArrayPaths.addAll(odtDoc.detectRepetitionGroupPaths());
            }

            arrayPaths.addAll(detectedArrayPaths);

            LOG.debugf("Found array paths (structural): %s", arrayPaths);
            LOG.debugf("Field names: %s", fieldNames);

            // Step 4: Extract and validate conditions (syntax only), collect field paths from them
            List<TemplateElement> conditionalElements = doc.collectConditionalTemplateElements();
            for (TemplateElement element : conditionalElements) {
                // Use text:condition attribute (JEXL expression) — not text:name (section name)
                String elementName = (element instanceof OdtTemplateElement odtEl)
                        ? odtEl.getCondition()
                        : element.getName();
                if (elementName == null || elementName.isBlank()) continue;
                boolean syntaxValid = true;
                String errorMessage = null;

                // Try to validate condition syntax
                try {
                    JexlConditionEvaluator.evaluate(elementName);
                    conditionExpressions.add(elementName);
                } catch (IllegalArgumentException e) {
                    syntaxValid = false;
                    errorMessage = e.getMessage();
                    errors.add(new ValidationResult.ValidationMessage(
                        "INVALID_CONDITION",
                        "Condition syntax error: " + e.getMessage()
                    ));
                } catch (Exception e) {
                    // Ignore other exceptions (they might be context-related)
                    conditionExpressions.add(elementName);
                }

                if (!syntaxValid) {
                    warnings.add(new ValidationResult.ValidationMessage(
                        "INVALID_CONDITION_SYNTAX",
                        "Condition '" + elementName + "' has syntax error: " + errorMessage
                    ));
                }

                // Extract field paths referenced in condition expressions so they appear in the schema.
                // E.g. 'customer.gender == "FEMALE"' → add "customer.gender" to fieldNames.
                var matcher = FIELD_PATH_IN_CONDITION.matcher(elementName);
                while (matcher.find()) {
                    String path = matcher.group();
                    if (!uniqueFields.contains(path) && VALID_FIELD_NAME.matcher(path).matches()) {
                        uniqueFields.add(path);
                        fieldNames.add(path);
                        LOG.debugf("Added condition field path to schema: %s", path);
                    }
                }
            }

            // Step 5: Generate JSON-Schema from fields (incl. condition fields) and arrays
            schema = schemaGenerator.generateSchema(fieldNames, arrayPaths, fieldValues, fieldTypes);
            LOG.debugf("Generated schema: %s", schema);

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
            warnings,
            new ArrayList<>(conditionExpressions),
            new ArrayList<>(detectedArrayPaths)
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
            LOG.debugf("Could not extract field value: %s", e.getMessage());
        }
        return null;
    }

    /**
     * Extracts the JSON schema type of a user field from the ODT office:value-type attribute.
     * Maps ODF value types to JSON schema types:
     * - "float", "percentage", "currency" → "number"
     * - "boolean" → "boolean"
     * - everything else (string, date, time, null) → "string"
     *
     * @param field the template element representing a user field
     * @return the JSON schema type ("number", "boolean", or "string"), or null if not determinable
     */
    private String extractFieldType(TemplateElement field) {
        try {
            if (field instanceof OdtTemplateElement odtElement) {
                String valueType = odtElement.getValueType();
                if (valueType == null) {
                    return null;
                }
                return switch (valueType) {
                    case "float", "percentage", "currency" -> "number";
                    case "boolean" -> "boolean";
                    default -> "string";
                };
            }
        } catch (Exception e) {
            LOG.debugf("Could not extract field type: %s", e.getMessage());
        }
        return null;
    }
}
