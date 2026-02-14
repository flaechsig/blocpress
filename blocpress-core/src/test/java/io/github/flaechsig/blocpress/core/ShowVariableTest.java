package io.github.flaechsig.blocpress.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;

import static io.github.flaechsig.blocpress.util.ResourceUtil.loadDocumentAsBytes;
import static io.github.flaechsig.blocpress.util.ResourceUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ShowVariableTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI baseUri =  Path.of(System.getProperty("user.dir"),  "src/test/resources")
            .toAbsolutePath()
            .toUri();

    @Test
    public void renderTemplate() throws Exception {
        String json = new String(loadDocumentAsBytes("/kuendigung.json"));
        JsonNode node = mapper.readTree(json);

        var expected = extractOdtContent(loadDocumentAsBytes("/kuendigung_generated.odt"));
        var actual = extractOdtContent(RenderEngine.mergeTemplate(baseUri.resolve("kuendigung.odt").normalize().toURL(), node));

        assertNotNull(actual);
        assertEquals(expected, actual);
    }

    @Test
    public void testNumberFormat() throws Exception{
        String json = """
                {
                  "numbertest": -98765.4321
                }
                """;
        JsonNode node = mapper.readTree(json);
        var actual = extractOdtContent(RenderEngine.mergeTemplate(baseUri.resolve("numberformats.odt").normalize().toURL(), node));

        var expected = """
                -98765
                -98765,43
                -98.765
                -98.765,43
                -98765 %
                -98765,43 %
                -98.765 €
                -98.765,43 €
                -98.765,43 EUR""";
        assertNotNull(actual);
        assertEquals(expected, actual);
    }
}
