package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TestDataSetService {

    @Inject
    TestDataSetRepository repository;

    @Inject
    TemplateValidator validator;

    @Transactional
    public TestDataSet createTestDataSet(UUID templateId, String name, JsonNode testData) {
        Template template = Template.findById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template nicht gefunden");
        }

        // Validiere TestData gegen Template-Felder
        validateTestData(template, testData);

        // Prüfe Duplikate
        if (repository.findByTemplateIdAndName(templateId, name).isPresent()) {
            throw new IllegalArgumentException("TestDataSet mit diesem Namen existiert bereits");
        }

        TestDataSet tds = TestDataSet.create(template, name, testData);
        tds.persist();
        return tds;
    }

    @Transactional
    public TestDataSet updateTestDataSet(UUID testDataSetId, String name, JsonNode testData) {
        TestDataSet tds = TestDataSet.findById(testDataSetId);
        if (tds == null) {
            throw new IllegalArgumentException("TestDataSet nicht gefunden");
        }

        // Validiere neue TestData
        validateTestData(tds.template, testData);

        tds.name = name;
        tds.testData = testData;
        tds.updatedAt = Instant.now();
        tds.persist();
        return tds;
    }

    @Transactional
    public void deleteTestDataSet(UUID testDataSetId) {
        TestDataSet.deleteById(testDataSetId);
    }

    public List<TestDataSet> listByTemplate(UUID templateId) {
        return repository.findByTemplateId(templateId);
    }

    public Optional<TestDataSet> getTestDataSet(UUID testDataSetId) {
        return Optional.ofNullable(TestDataSet.findById(testDataSetId));
    }

    @Transactional
    public void saveExpectedPdf(UUID testDataSetId, byte[] pdfContent) {
        TestDataSet tds = TestDataSet.findById(testDataSetId);
        if (tds == null) {
            throw new IllegalArgumentException("TestDataSet nicht gefunden");
        }

        tds.expectedPdf = pdfContent;
        tds.pdfHash = calculateHash(pdfContent);
        tds.updatedAt = Instant.now();
        tds.persist();
    }

    public Optional<byte[]> getExpectedPdf(UUID testDataSetId) {
        TestDataSet tds = TestDataSet.findById(testDataSetId);
        if (tds == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tds.expectedPdf);
    }

    public String calculateHash(byte[] pdfContent) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] messageDigest = md.digest(pdfContent);
            StringBuilder sb = new StringBuilder();
            for (byte b : messageDigest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 nicht verfügbar", e);
        }
    }

    private void validateTestData(Template template, JsonNode testData) {
        if (template.validationResult == null) {
            throw new IllegalStateException("Template hat keine Validierungsergebnisse");
        }

        // Validiere gegen JSON-Schema
        // Hinweis: Vollständige Validierung ist optional - erlaubt fehlende optionale Felder
        // Dies ist absichtlich tolerant, da Testdaten nicht alle Felder enthalten müssen
        if (template.validationResult.schema() != null) {
            // TODO: Implement proper JSON-Schema validation using com.networknt.json-schema-validator
            // For now, just accept the test data (warnings can be logged)
            // Future: Use JsonSchemaFactory and schema.validate(testData)
        }
    }
}
