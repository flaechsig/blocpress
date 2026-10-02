package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flaechsig.blocpress.workbench.entity.ValidationResult;
import io.github.flaechsig.blocpress.workbench.service.JsonSchemaGenerator;
import io.github.flaechsig.blocpress.workbench.service.TemplateValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Felder und Bedingungen, die nur in Kopf-/Fusszeilen vorkommen, muessen in der Workbench als
 * Platzhalter der Vorlage erkannt werden — sonst kann man sie nicht befuellen (REQ-0010).
 * {@code header_footer.odt}: {@code offer.supportId} steht ausschliesslich in der linken Fusszeile,
 * die Bedingung auf {@code kunde.anrede} ausschliesslich in der rechten Fusszeile.
 */
class HeaderFooterFieldDetectionTest {

    private static final Path TEMPLATE = Path.of("../blocpress-core/src/test/resources/header_footer.odt");

    private TemplateValidator validator;

    @BeforeEach
    void setUp() {
        var mapper = new ObjectMapper();
        var schemaGenerator = new JsonSchemaGenerator();
        schemaGenerator.objectMapper = mapper;
        validator = new TemplateValidator();
        validator.objectMapper = mapper;
        validator.schemaGenerator = schemaGenerator;
    }

    @Test
    @Tag("REQ-0010")
    void footerOnlyFieldsAndConditionsAreDetected() throws Exception {
        assertDetected(validator.validate(Files.readAllBytes(TEMPLATE)));
    }

    @Test
    @Tag("REQ-0010")
    void footerOnlyFieldsAreDetectedWithoutDeclarations() throws Exception {
        // Fallback des Validators: ohne text:user-field-decls werden die Felder aus den Verwendungen gelesen
        assertDetected(validator.validate(withoutDeclarations(Files.readAllBytes(TEMPLATE))));
    }

    private static void assertDetected(ValidationResult result) {
        assertTrue(result.errors().isEmpty(), "Validierungsfehler: " + result.errors());
        JsonNode offer = result.schema().path("properties").path("offer").path("properties");
        assertTrue(offer.has("supportId"), "Fusszeilen-Feld fehlt im Schema: " + result.schema());
        assertTrue(offer.has("reference"), result.schema().toString());
        assertTrue(result.conditions().stream().anyMatch(c -> c.contains("kunde.anrede")),
                "Fusszeilen-Bedingung fehlt: " + result.conditions());
    }

    /** Kopie der Vorlage, in deren content.xml die text:user-field-decls entfernt sind. */
    private static byte[] withoutDeclarations(byte[] odt) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var in = new ZipInputStream(new ByteArrayInputStream(odt)); var zip = new ZipOutputStream(out)) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                byte[] data = in.readAllBytes();
                if (e.getName().equals("content.xml")) {
                    data = new String(data, StandardCharsets.UTF_8)
                            .replaceAll("<text:user-field-decls>.*?</text:user-field-decls>", "")
                            .getBytes(StandardCharsets.UTF_8);
                }
                ZipEntry copy = new ZipEntry(e.getName());
                if (e.getName().equals("mimetype")) {
                    copy.setMethod(ZipEntry.STORED);
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
}
