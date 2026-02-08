package io.github.flaechsig.blocpress.renderer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;

import static io.github.flaechsig.blocpress.util.ResourceUtil.extractOdtContent;
import static io.github.flaechsig.blocpress.util.ResourceUtil.loadDocumentAsBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class IfConditionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI baseUri =  Path.of(System.getProperty("user.dir"),  "src/test/resources")
            .toAbsolutePath()
            .toUri();

    @Test
    public void testIfConditionTrue() throws Exception {
        String json = """
                {
                  "kunde": {
                    "anrede": "FRAU",
                    "nachname": "Müller"
                  }
                }
                """;
        JsonNode node = mapper.readTree(json);
        var actual = extractOdtContent(RenderEngine.renderTemplate(baseUri.resolve("IfCondition.odt").normalize().toURL(), node));
        assertNotNull(actual);
        assertEquals("Liebe Frau Müller", actual);
    }


    @Test
    public void testIfConditionFalse() throws Exception {
        String json = """
                {
                  "kunde": {
                    "anrede": "HERR",
                    "nachname": "Müller"
                  }
                }
                """;
        JsonNode node = mapper.readTree(json);
        var actual = extractOdtContent(RenderEngine.renderTemplate(baseUri.resolve("IfCondition.odt").normalize().toURL(), node));
        assertNotNull(actual);
        assertEquals("Lieber Herr Müller", actual);
    }
}
