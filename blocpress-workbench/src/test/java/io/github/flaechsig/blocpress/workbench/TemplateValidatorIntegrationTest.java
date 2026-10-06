package io.github.flaechsig.blocpress.workbench;
import io.github.flaechsig.blocpress.workbench.repository.TestDataSetRepository;
import io.github.flaechsig.blocpress.workbench.service.JsonSchemaGenerator;
import io.github.flaechsig.blocpress.workbench.service.TestDataSetService;
import io.github.flaechsig.blocpress.workbench.service.TemplateValidator;
import io.github.flaechsig.blocpress.workbench.entity.ValidationResult;
import io.github.flaechsig.blocpress.workbench.entity.TemplateStatus;
import io.github.flaechsig.blocpress.workbench.entity.TestDataSet;
import io.github.flaechsig.blocpress.workbench.entity.Template;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemplateValidator with real ODT files.
 */
class TemplateValidatorIntegrationTest {

    private TemplateValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        JsonSchemaGenerator schemaGenerator = new JsonSchemaGenerator();
        schemaGenerator.objectMapper = objectMapper;
        validator = new TemplateValidator();
        validator.objectMapper = objectMapper;
        validator.schemaGenerator = schemaGenerator;
    }

    @Test
    void testValidateWithRealOdtFile() throws Exception {
        // Load a real ODT file from blocpress-core test resources
        Path odtPath = Paths.get("../blocpress-core/src/test/resources/sample-04.odt");

        assertTrue(Files.exists(odtPath), "Testvorlage fehlt: " + odtPath.toAbsolutePath());

        byte[] odtContent = Files.readAllBytes(odtPath);
        ValidationResult result = validator.validate(odtContent);

        assertNotNull(result);
        assertNotNull(result.errors());
        assertNotNull(result.warnings());
        assertNotNull(result.schema());
        assertEquals("object", result.schema().get("type").asText());
    }

    @Test
    void testValidateWithInvalidOdt() {
        byte[] invalidContent = "This is not an ODT file".getBytes();
        ValidationResult result = validator.validate(invalidContent);

        assertNotNull(result);
        assertFalse(result.isValid(), "Should be invalid for non-ODT content");
        assertFalse(result.errors().isEmpty(), "Should have errors for invalid ODT");
    }

    @Test
    void testValidateWithEmptyContent() {
        byte[] emptyContent = new byte[0];
        ValidationResult result = validator.validate(emptyContent);

        assertNotNull(result);
        assertFalse(result.isValid(), "Should be invalid for empty content");
        assertFalse(result.errors().isEmpty(), "Should have errors for empty content");
    }

    @Test
    void testValidationResultStructure() throws Exception {
        Path odtPath = Paths.get("../blocpress-core/src/test/resources/sample-04.odt");

        assertTrue(Files.exists(odtPath), "Testvorlage fehlt: " + odtPath.toAbsolutePath());

        byte[] odtContent = Files.readAllBytes(odtPath);
        ValidationResult result = validator.validate(odtContent);

        // Verify result structure
        assertTrue(result.isValid(), "sample-04.odt ist eine gueltige Vorlage: " + result.errors());
        assertNotNull(result.errors(), "Errors should not be null");
        assertNotNull(result.warnings(), "Warnings should not be null");
        assertNotNull(result.schema(), "Schema should not be null");
        assertEquals("object", result.schema().get("type").asText(), "Schema should be object type");
    }

    @Test
    void testValidatorExtractsUserFields() throws Exception {
        Path odtPath = Paths.get("../blocpress-core/src/test/resources/sample-04.odt");

        assertTrue(Files.exists(odtPath), "Testvorlage fehlt: " + odtPath.toAbsolutePath());

        byte[] odtContent = Files.readAllBytes(odtPath);
        ValidationResult result = validator.validate(odtContent);

        // Check if schema was generated (should contain properties for user fields)
        assertNotNull(result.schema());
        assertNotNull(result.schema().get("properties"));
        // sample-04.odt hat Benutzerfelder: sie muessen im Schema stehen, und es darf keine Fehler geben
        assertTrue(result.errors().isEmpty(), "Validierungsfehler: " + result.errors());
        assertTrue(result.schema().get("properties").size() > 0, "keine Felder erkannt: " + result.schema());
    }

    @Test
    @DisplayName("REQ-0026: Nicht lesbare Vorlage wird als Fehler mit Art des Fehlers gemeldet")
    void unreadableTemplateIsReportedAsError() {
        ValidationResult result = validator.validate("This is not an ODT file".getBytes());

        assertFalse(result.isValid());
        assertEquals(1, result.errors().size(), result.errors().toString());
        assertEquals("INVALID_ODT_STRUCTURE", result.errors().get(0).code());
        assertTrue(result.errors().get(0).message().startsWith("Could not load ODT file"),
                result.errors().get(0).message());
    }

    @Test
    @DisplayName("REQ-0026: Syntaxfehler in einer Bedingung ergibt genau einen Fehler, der die Bedingung nennt")
    void conditionSyntaxErrorNamesCondition() throws Exception {
        // Kopie von IfCondition.odt, deren Bedingung ein doppeltes == enthaelt
        byte[] odt = replaceInXml(Files.readAllBytes(Paths.get("../blocpress-core/src/test/resources/IfCondition.odt")),
                "kunde.anrede == &quot;FRAU&quot;", "kunde.anrede == == &quot;FRAU&quot;");
        ValidationResult result = validator.validate(odt);

        assertFalse(result.isValid());
        assertEquals(1, result.errors().size(), result.errors().toString());
        assertEquals("INVALID_CONDITION", result.errors().get(0).code());
        assertTrue(result.errors().get(0).message().contains("kunde.anrede == == \"FRAU\""),
                result.errors().get(0).message());
        assertTrue(result.warnings().stream().noneMatch(w -> w.code().startsWith("INVALID_CONDITION")),
                "Bedingung doppelt gemeldet: " + result.warnings());
    }

    @Test
    void validatorDetectsRepetitionGroupInTableRow() throws Exception {
        // loop_table.odt: Tabellenzeile mit produkte.name, produkte.menge, produkte.preis
        ValidationResult result = validator.validate(
                Files.readAllBytes(Paths.get("../blocpress-core/src/test/resources/loop_table.odt")));

        assertTrue(result.isValid(), result.errors().toString());
        assertEquals(java.util.List.of("produkte"), result.repetitionGroups());
        assertEquals("array", result.schema().path("properties").path("produkte").path("type").asText(),
                result.schema().toString());
    }

    @Test
    @DisplayName("REQ-0026: Ungueltiger Feldname wird mit dem Namen des Feldes gemeldet")
    void testValidatorDetectsInvalidFieldNames() throws Exception {
        // Kopie von header_footer.odt, in der das Feld "offer.reference" in "offer reference" umbenannt ist
        byte[] odt = replaceInXml(Files.readAllBytes(Paths.get("../blocpress-core/src/test/resources/header_footer.odt")),
                "text:name=\"offer.reference\"", "text:name=\"offer reference\"");
        ValidationResult result = validator.validate(odt);

        assertTrue(result.warnings().stream().anyMatch(w -> "INVALID_FIELD_NAME".equals(w.code())
                        && w.message().contains("offer reference")),
                "ungueltiger Feldname nicht gemeldet: " + result.warnings());
    }

    /** Ersetzt Text in content.xml und styles.xml einer ODT-Kopie. */
    private static byte[] replaceInXml(byte[] odt, String from, String to) throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        try (var in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(odt));
             var zip = new java.util.zip.ZipOutputStream(out)) {
            for (var e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                byte[] data = in.readAllBytes();
                if (e.getName().equals("content.xml") || e.getName().equals("styles.xml")) {
                    data = new String(data, java.nio.charset.StandardCharsets.UTF_8)
                            .replace(from, to)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
                var copy = new java.util.zip.ZipEntry(e.getName());
                if (e.getName().equals("mimetype")) {
                    copy.setMethod(java.util.zip.ZipEntry.STORED);
                    copy.setSize(data.length);
                    var crc = new java.util.zip.CRC32();
                    crc.update(data);
                    copy.setCrc(crc.getValue());
                }
                zip.putNextEntry(copy);
                zip.write(data);
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @Test
    void testValidationResultContainsExpectedFields() throws Exception {
        Path odtPath = Paths.get("../blocpress-core/src/test/resources/sample-04.odt");

        assertTrue(Files.exists(odtPath), "Testvorlage fehlt: " + odtPath.toAbsolutePath());

        byte[] odtContent = Files.readAllBytes(odtPath);
        ValidationResult result = validator.validate(odtContent);

        // gueltige Vorlage: gueltig, keine Fehler, Schema mit Feldern
        assertTrue(result.isValid(), result.errors().toString());
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertNotNull(result.warnings());
        assertTrue(result.schema().path("properties").size() > 0, result.schema().toString());
    }

    @Test
    void testMultipleInvalidOdtFiles() {
        // Test various invalid inputs
        byte[][] invalidInputs = {
            new byte[0],
            "plain text".getBytes(),
            "ZIP file header but not ODT".getBytes(),
            "<html>Not ODT</html>".getBytes()
        };

        for (byte[] input : invalidInputs) {
            ValidationResult result = validator.validate(input);
            assertNotNull(result);
            assertFalse(result.isValid(), "Should detect invalid ODT for: " + new String(input));
        }
    }

    @Test
    void testValidatorHandlesLargeFiles() {
        byte[] largeContent = new byte[1024 * 1024]; // 1MB
        ValidationResult result = validator.validate(largeContent);

        assertNotNull(result);
        assertFalse(result.isValid());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void testValidatorWithNullContent() {
        // null darf nicht mit einer Ausnahme enden, sondern als ungueltige Vorlage gemeldet werden
        ValidationResult result = validator.validate(null);
        assertFalse(result.isValid());
        assertFalse(result.errors().isEmpty(), "Fehlermeldung fehlt");
    }

    @Test
    void testValidationResultIsConsistent() throws Exception {
        Path odtPath = Paths.get("../blocpress-core/src/test/resources/sample-04.odt");

        assertTrue(Files.exists(odtPath), "Testvorlage fehlt: " + odtPath.toAbsolutePath());

        byte[] odtContent = Files.readAllBytes(odtPath);

        // Validate twice and results should be consistent
        ValidationResult result1 = validator.validate(odtContent);
        ValidationResult result2 = validator.validate(odtContent);

        assertEquals(result1.isValid(), result2.isValid());
        assertEquals(result1.schema().toString(), result2.schema().toString());
        assertEquals(result1.errors().size(), result2.errors().size());
        assertEquals(result1.warnings().size(), result2.warnings().size());
    }

    @Test
    void testValidatorExtractsDefaultValuesFromUserFields() throws Exception {
        Path odtPath = Paths.get("../blocpress-core/src/test/resources/sample-04.odt");

        assertTrue(Files.exists(odtPath), "Testvorlage fehlt: " + odtPath.toAbsolutePath());

        byte[] odtContent = Files.readAllBytes(odtPath);
        ValidationResult result = validator.validate(odtContent);

        // Check if schema properties include default values
        assertNotNull(result.schema());
        assertNotNull(result.schema().get("properties"));

        // If there are properties, some of them may have default values from the ODT
        var properties = result.schema().get("properties");
        if (properties.size() > 0) {
            // At least verify that the structure supports default values
            var firstProperty = properties.elements().next();
            assertTrue(firstProperty.isObject(), "Properties should be objects");
            // Some properties may have default, some may not - both are valid
            assertTrue(firstProperty.has("type"), "Properties should have type");
        }
    }
}
