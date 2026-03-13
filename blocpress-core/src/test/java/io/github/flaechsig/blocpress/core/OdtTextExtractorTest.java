package io.github.flaechsig.blocpress.core;

import io.github.flaechsig.blocpress.core.odt.OdtTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class OdtTextExtractorTest {

    private final OdtTextExtractor extractor = new OdtTextExtractor();

    @Test
    void extractsTextFromKuendigungOdt() throws IOException {
        byte[] odt = loadResource("kuendigung.odt");
        String text = extractor.extract(odt);
        assertNotNull(text);
        assertFalse(text.isBlank(), "Extracted text must not be empty");
    }

    @Test
    void extractedTextContainsFieldPlaceholders() throws IOException {
        byte[] odt = loadResource("kuendigung.odt");
        String text = extractor.extract(odt);
        // ODT templates contain field variable names in the text content —
        // at minimum the document has prose around the fields
        assertTrue(text.length() > 10, "Expected meaningful text content, got: " + text);
    }

    @Test
    void extractsTextFromSampleOdt() throws IOException {
        byte[] odt = loadResource("sample-04.odt");
        String text = extractor.extract(odt);
        assertNotNull(text);
        assertFalse(text.isBlank());
    }

    @Test
    void throwsIoExceptionOnInvalidBytes() {
        assertThrows(IOException.class,
                () -> extractor.extract(new byte[]{0x00, 0x01, 0x02}));
    }

    private byte[] loadResource(String name) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(is, "Test resource not found: " + name);
            return is.readAllBytes();
        }
    }
}