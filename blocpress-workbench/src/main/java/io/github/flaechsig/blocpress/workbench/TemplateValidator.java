package io.github.flaechsig.blocpress.workbench;

import io.github.flaechsig.blocpress.core.TemplateDocument;
import io.github.flaechsig.blocpress.core.TemplateElement;
import io.github.flaechsig.blocpress.core.odt.JexlConditionEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@ApplicationScoped
public class TemplateValidator {

    private static final Pattern VALID_FIELD_NAME =
        Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$");

    @Inject
    ObjectMapper objectMapper;

    public ValidationResult validate(byte[] templateContent) {
        List<ValidationResult.ValidationMessage> errors = new ArrayList<>();
        List<ValidationResult.ValidationMessage> warnings = new ArrayList<>();
        List<ValidationResult.UserFieldInfo> userFields = new ArrayList<>();
        List<ValidationResult.RepetitionGroupInfo> repetitionGroups = new ArrayList<>();
        List<ValidationResult.ConditionInfo> conditions = new ArrayList<>();

        Path tempFile = null;
        try {
            // Step 1: Create temporary file and load ODT document
            tempFile = Files.createTempFile("template-validation-", ".odt");
            Files.write(tempFile, templateContent);
            URL templateUrl = tempFile.toUri().toURL();

            TemplateDocument doc = TemplateDocument.load(templateUrl);

            // Step 2: Extract and validate user fields
            List<TemplateElement> extractedFields = doc.collectUserFields();
            for (TemplateElement field : extractedFields) {
                String name = field.getName();
                // Assume "user-field-get" since we don't have access to the element details
                String type = "user-field";

                userFields.add(new ValidationResult.UserFieldInfo(name, type));

                // Check field name format
                if (!VALID_FIELD_NAME.matcher(name).matches()) {
                    warnings.add(new ValidationResult.ValidationMessage(
                        "INVALID_FIELD_NAME",
                        "Field '" + name + "' does not follow dot-notation pattern"
                    ));
                }
            }

            // Step 3: Identify repetition groups (loops)
            ObjectNode emptyData = objectMapper.createObjectNode();
            Map<TemplateElement, String> repeatGroups = doc.findRepeatGroups(emptyData);
            for (Map.Entry<TemplateElement, String> entry : repeatGroups.entrySet()) {
                TemplateElement element = entry.getKey();
                String arrayPath = entry.getValue();
                // Assume "section" since we don't have access to the element details
                String elementType = "section";

                repetitionGroups.add(new ValidationResult.RepetitionGroupInfo(
                    element.getName(),
                    arrayPath,
                    elementType
                ));
            }

            // Step 4: Extract and validate conditions (syntax only)
            List<TemplateElement> conditionalElements = doc.collectConditionalTemplateElements();
            for (TemplateElement element : conditionalElements) {
                String elementName = element.getName();
                String elementType = "conditional-element";
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

                conditions.add(new ValidationResult.ConditionInfo(
                    elementName,
                    elementType,
                    syntaxValid,
                    errorMessage
                ));
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

        return new ValidationResult(
            isValid,
            errors,
            warnings,
            userFields,
            repetitionGroups,
            conditions
        );
    }
}
