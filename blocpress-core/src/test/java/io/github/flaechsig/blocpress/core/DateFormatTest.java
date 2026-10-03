package io.github.flaechsig.blocpress.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static io.github.flaechsig.blocpress.util.ResourceUtil.extractOdtContent;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Datumsformate von Benutzerfeldern (REQ-0005/0006: "number and date formatting").
 * {@code dateformats.odt}: Feld {@code datumtest} in fuenf Datumsformaten — deutsch, ISO, kurz,
 * Datum mit Uhrzeit, ohne Sprachangabe.
 */
class DateFormatTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI baseUri = Path.of(System.getProperty("user.dir"), "src/test/resources").toAbsolutePath().toUri();

    @ParameterizedTest(name = "{0}")
    @Tag("REQ-0006")
    @CsvSource(delimiter = '|', value = {
            "2026-10-03T14:30:00 | DE 03.10.2026 | ISO 2026-10-03 | KURZ 3.10.26 | ZEIT 03.10.2026 14:30 | OHNE 03/10/2026",
            "2026-10-03          | DE 03.10.2026 | ISO 2026-10-03 | KURZ 3.10.26 | ZEIT 03.10.2026 00:00 | OHNE 03/10/2026",
            "03.10.2026          | DE 03.10.2026 | ISO 2026-10-03 | KURZ 3.10.26 | ZEIT 03.10.2026 00:00 | OHNE 03/10/2026",
            "03.10.2026 14:30    | DE 03.10.2026 | ISO 2026-10-03 | KURZ 3.10.26 | ZEIT 03.10.2026 14:30 | OHNE 03/10/2026"
    })
    void dateFieldsAreFormattedPerStyle(String input, String de, String iso, String kurz, String zeit, String ohne)
            throws Exception {
        var node = mapper.readTree("{\"datumtest\":\"" + input + "\"}");
        List<String> lines = extractOdtContent(RenderEngine.mergeTemplate(
                baseUri.resolve("dateformats.odt").toURL(), node, Locale.GERMANY)).lines().toList();
        assertEquals(List.of(de, iso, kurz, zeit, ohne), lines);
    }
}
