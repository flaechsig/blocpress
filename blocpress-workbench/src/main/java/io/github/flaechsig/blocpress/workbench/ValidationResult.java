package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Template validation result with JSON-Schema representation.
 *
 * The 'schema' field contains a complete JSON-Schema that can be used for:
 * - Form generation (UI knows all available fields and types)
 * - Data validation (before render)
 * - API documentation
 * - Code generation
 *
 * JSON-Schema format:
 * {
 *   "type": "object",
 *   "properties": {
 *     "customer": {
 *       "type": "object",
 *       "properties": {
 *         "name": { "type": "string" },
 *         "email": { "type": "string" }
 *       },
 *       "required": ["name"]
 *     },
 *     "items": {
 *       "type": "array",
 *       "items": {
 *         "type": "object",
 *         "properties": { "description": { "type": "string" }, ... }
 *       }
 *     }
 *   }
 * }
 */
public record ValidationResult(
    boolean isValid,
    JsonNode schema,  // JSON-Schema (https://json-schema.org/)
    List<ValidationMessage> errors,
    List<ValidationMessage> warnings
) {
    public record ValidationMessage(String code, String message) {}
}
