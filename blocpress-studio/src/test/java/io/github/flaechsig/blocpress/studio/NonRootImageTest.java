package io.github.flaechsig.blocpress.studio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-0050: Das Image startet den Dienst als unprivilegierten Benutzer mit numerischer UID. */
class NonRootImageTest {

    private static final Pattern USER = Pattern.compile("^USER\\s+(\\S+)\\s*$");
    private static final Pattern NUMERIC = Pattern.compile("(\\d+)(?::\\d+)?");

    @ParameterizedTest(name = "{displayName} [{0}]")
    @DisplayName("REQ-0050: imageRunsServiceAsNonRootNumericUser")
    @ValueSource(strings = {"Dockerfile", "Dockerfile.native"})
    void imageRunsServiceAsNonRootNumericUser(String dockerfile) throws Exception {
        String user = null;
        for (String line : Files.readAllLines(Path.of(dockerfile))) {
            Matcher m = USER.matcher(line.strip());
            if (m.matches()) {
                user = m.group(1);  // die letzte USER-Anweisung gilt fuer CMD
            }
        }
        assertNotNull(user, dockerfile + " setzt kein USER");
        Matcher uid = NUMERIC.matcher(user);
        assertTrue(uid.matches(), dockerfile + ": USER " + user + " ist keine numerische UID");
        assertNotEquals(0, Integer.parseInt(uid.group(1)), dockerfile + ": USER ist root");
    }
}
