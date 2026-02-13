package io.github.flaechsig.blocpress.renderer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TextBlockTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI baseUri = Path.of(System.getProperty("user.dir"), "src/test/resources")
            .toAbsolutePath()
            .toUri();

    @Test
    public void testTextBlock() throws Exception {
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
                            "gender": "FEMALE",
                            "firstName": "Mini",
                            "lastName": "Müller",
                            "address": {
                              "street": "Main Street 1",
                              "postcode": "54321",
                              "city": "Munich"
                            },
                            "contact": {
                              "email": "mini.mueller@example.com"
                            }
                          }
                        ]
                    }
                """;
        JsonNode node = mapper.readTree(json);
        var actual = RenderEngine.mergeTemplate(baseUri.resolve("sample-05.odt").normalize().toURL(), node);
        assertNotNull(actual);
        Files.write(Files.createTempFile("textblock-", ".odt"), actual);
    }
}
