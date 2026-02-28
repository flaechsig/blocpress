package io.github.flaechsig.blocpress.workbench;

import io.github.flaechsig.blocpress.core.TemplateDocument;
import io.github.flaechsig.blocpress.core.TemplateElement;
import io.github.flaechsig.blocpress.core.odt.JexlConditionEvaluator;
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
            ObjectNode emptyData = objectMapper.createObjectNode();
            Map<TemplateElement, String> repeatGroups = doc.findRepeatGroups(emptyData);
            for (Map.Entry<TemplateElement, String> entry : repeatGroups.entrySet()) {
                String arrayPath = entry.getValue();
                arrayPaths.add(arrayPath);
            }

            // Step 4: Generate JSON-Schema from fields and arrays
            schema = schemaGenerator.generateSchema(fieldNames, arrayPaths);

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
}
