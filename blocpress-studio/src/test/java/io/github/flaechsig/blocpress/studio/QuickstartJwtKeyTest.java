package io.github.flaechsig.blocpress.studio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-0053: Das Quickstart-Image enthaelt keinen eingebauten Schluessel zur Token-Pruefung.
 * Ins Image kommen die Dateien aus {@code docker/studio/} und die drei Anwendungen; geprueft
 * wird, dass keine davon einen Pruefschluessel setzt oder Schluesselmaterial mitbringt.
 */
class QuickstartJwtKeyTest {

    /** Setzt die MicroProfile-JWT-Variable bzw. -Eigenschaft fuer den Pruefschluessel. */
    private static final Pattern SETS_KEY = Pattern.compile(
            "(?i)(MP_JWT_VERIFY_PUBLICKEY(_LOCATION)?|mp\\.jwt\\.verify\\.publickey(\\.location)?)\\s*[=:]");
    /** Ein RSA-Public-Key, als PEM oder als Base64 (SubjectPublicKeyInfo beginnt mit MIIB/MIIC). */
    private static final Pattern KEY_MATERIAL = Pattern.compile("BEGIN PUBLIC KEY|\"MII[BC]I[A-Za-z0-9+/]{20,}");

    @Test
    @DisplayName("REQ-0053: quickstartImageHasNoBuiltInVerificationKey")
    void quickstartImageHasNoBuiltInVerificationKey() throws IOException {
        List<Path> files = imageSources();
        assertTrue(files.contains(Path.of("../docker/studio/Dockerfile")), "Quickstart-Dockerfile fehlt: " + files);
        assertTrue(files.contains(Path.of("../docker/studio/Dockerfile.native")), "Natives Dockerfile fehlt: " + files);

        for (Path file : files) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.stripLeading().startsWith("#")) {
                    continue;  // Kommentare duerfen die Variable nennen
                }
                String where = file + ":" + (i + 1);
                assertFalse(SETS_KEY.matcher(line).find(), where + " setzt einen Pruefschluessel: " + line);
                assertFalse(KEY_MATERIAL.matcher(line).find(), where + " enthaelt Schluesselmaterial");
            }
        }
    }

    /** Dateien unter docker/studio und die Ressourcen der drei Anwendungen im Image. */
    private static List<Path> imageSources() throws IOException {
        List<Path> roots = List.of(
                Path.of("../docker/studio"),
                Path.of("../blocpress-render/src/main/resources"),
                Path.of("../blocpress-workbench/src/main/resources"),
                Path.of("src/main/resources"));
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .filter(QuickstartJwtKeyTest::isText)
                        .forEach(files::add);
            }
        }
        return files;
    }

    private static boolean isText(Path file) {
        String name = file.getFileName().toString();
        return !name.endsWith(".odt") && !name.endsWith(".png") && !name.endsWith(".ico")
                && !name.endsWith(".jpg") && !name.endsWith(".pdf") && !name.endsWith(".woff2");
    }
}
