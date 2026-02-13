package io.github.flaechsig.blocpress.renderer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.flaechsig.blocpress.util.ResourceUtil.extractOdtContent;
import static io.github.flaechsig.blocpress.util.ResourceUtil.loadDocumentAsBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LoopTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI baseUri = Path.of(System.getProperty("user.dir"), "src/test/resources")
            .toAbsolutePath()
            .toUri();

    @Test
    public void testProductLoop() throws Exception {
        String json = """
                {
                  "kunde": "Max Mustermann",
                  "produkte": [
                    {"name": "Apfel", "menge" : 1, "preis": 1.00},
                    {"name": "Birne", "menge" : 2, "preis": 1.50},
                    {"name": "Banane", "menge" : 3, "preis": 0.50}
                  ]
                }
                """;
        JsonNode node = mapper.readTree(json);
        var expected = extractOdtContent(loadDocumentAsBytes("/loop_table_expected.odt"));
        var actual = extractOdtContent(RenderEngine.mergeTemplate(baseUri.resolve("loop_table.odt").normalize().toURL(), node));

        assertNotNull(actual);
        assertEquals(expected, actual);
    }

    @Test
    public void testCustomerLoop() throws Exception {
        String json = """           
                {
                      "customer": [
                          {
                            "gender": "MALE",
                            "firstName": "Michael",
                            "lastName": "Miller",
                            "address": {
                              "street": "Example Street 1",
                              "postcode": "12345",
                              "city": "Hamburg"
                            },
                            "contact": {
                              "email": "michael.miller@example.com"
                            }
                          },
                          {
                            "gender": "MALE",
                            "firstName": "Michael",
                            "lastName": "Miller",
                            "address": {
                              "street": "Example Street 1",
                              "postcode": "12345",
                              "city": "Hamburg"
                            },
                            "contact": {
                              "email": "mini.miller@example.com"
                            }
                          }
                        ]
                    }
                """;
        JsonNode node = mapper.readTree(json);
//        var expected = extractOdtContent(loadDocumentAsBytes("/loop_table_expected.odt"));
        var actual = RenderEngine.mergeTemplate(baseUri.resolve("sample-04.odt").normalize().toURL(), node);
        Files.write(Path.of("/tmp", "sample-04.odt"), actual);
        assertNotNull(actual);
//        assertEquals(expected, actual);
    }
}
