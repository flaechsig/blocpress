package io.github.flaechsig.blocpress.renderer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;

import static io.github.flaechsig.blocpress.util.ResourceUtil.extractOdtContent;
import static io.github.flaechsig.blocpress.util.ResourceUtil.loadDocumentAsBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SectionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI baseUri =  Path.of(System.getProperty("user.dir"),  "src/test/resources")
            .toAbsolutePath()
            .toUri();

    @Test
    public void testSectionFrau() throws Exception {
        String json = """
                {
                  "kunde": {
                    "anrede": "FRAU",
                    "nachname": "Müller"
                  }
                }
                """;

        var expected = """
                Absatz der unter der Bedingung „kunde.anrede“ == „FRAU“ angezeigt werden soll. Hier folgt dann weiterer Text und auch der Nachname von Müller.""";
        JsonNode node = mapper.readTree(json);
        var actual = extractOdtContent(RenderEngine.renderTemplate(baseUri.resolve("section.odt").normalize().toURL(), node));
        assertNotNull(actual);
        assertEquals(expected, actual);
    }


    @Test
    public void testSectionHerr() throws Exception {
        String json = """
                {
                  "kunde": {
                    "anrede": "HERR",
                    "nachname": "Müller"
                  }
                }
                """;
        var expected = """
                Dieser Absatz wird für angezeigt, wenn die Anrede auf „HERR“ steht. Und dann kommt weiterer Text und auch der Nachname von Müller.""";
        JsonNode node = mapper.readTree(json);
        var actual = extractOdtContent(RenderEngine.renderTemplate(baseUri.resolve("section.odt").normalize().toURL(), node));
        assertNotNull(actual);
        assertEquals(expected, actual);
    }

    @Test
    public void testSectionDivers() throws Exception {
        String json = """
                {
                  "kunde": {
                    "anrede": "DIVERS",
                    "nachname": "Müller"
                  }
                }
                """;
        JsonNode node = mapper.readTree(json);
        byte[] odt = loadDocumentAsBytes("/section.odt");
        var expected = """
                Wenn weder Frau oder Herr im Attribut steht, dann wird für dieser Text angezeigt.""";
        var actual = extractOdtContent(RenderEngine.renderTemplate(baseUri.resolve("section.odt").normalize().toURL(), node));
        assertNotNull(actual);
        assertEquals(expected, actual);
    }
}
