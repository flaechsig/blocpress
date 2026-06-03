package io.github.flaechsig.blocpress.core;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Konvertiert ODT-Dokumente in andere Formate (PDF, RTF) mittels LibreOffice headless.
 * Startet einen externen {@code soffice}-Prozess zur Konvertierung.
 *
 * <p><b>Design-Referenzen:</b></p>
 * <ul>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-ti-3">TI-3: LibreOffice API</a></li>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-c-2">C-2: LibreOffice Version</a></li>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-c-5">C-5: Export-Formate</a></li>
 * </ul>
 */
public class LibreOfficeProcessor {

    /**
     * Basis-Arbeitsverzeichnis, zur Laufzeit aus {@code java.io.tmpdir} abgeleitet.
     * <p>Bewusst KEIN {@code static final} mit {@code user.home}: GraalVM-Native-Image
     * friert {@code static final}-Werte zur Build-Zeit ein (im Build-Container ist
     * {@code user.home} z. B. {@code /}), was zur Laufzeit zu {@code AccessDeniedException}
     * fuehrt. {@code java.io.tmpdir} wird im Native-Image zuverlaessig zur Laufzeit aufgeloest.</p>
     */
    private static Path workBase() {
        return Path.of(System.getProperty("java.io.tmpdir", "/tmp"), "blocpress");
    }

    /**
     * Refreshes and transforms document to specified output format
     */
    public static byte[] refreshAndTransform(byte[] input, @NonNull OutputFormat format) throws IOException {
        Path workBase = workBase();
        Files.createDirectories(workBase);
        Path in = Files.createTempFile(workBase, "blocpress_", ".odt");
        Path workDir = Files.createTempDirectory(workBase, "blocpress_out");
        // Eigenes LibreOffice-Profil pro Aufruf: ohne dedizierte UserInstallation teilen
        // sich parallele soffice-Prozesse das Default-Profil und kollidieren am Single-
        // Instance-Lock. Ein eindeutiges Profil macht den CLI-Pfad nebenlaeufigkeitssicher.
        Path userProfile = Files.createTempDirectory(workBase, "blocpress_profile");
        try {
            Files.write(in, input);
            var convert = switch (format) {
                case PDF -> "pdf";
                case RTF -> "rtf";
                case ODT -> "odt:writer8";
            };

            List<String> cmd = new ArrayList<>();
            cmd.add("soffice");
            cmd.add("-env:UserInstallation=" + userProfile.toUri());
            cmd.add("--headless");
            cmd.add("--nologo");
            cmd.add("--nodefault");
            cmd.add("--norestore");
            cmd.add("--nolockcheck");
            cmd.add("--invisible");
            cmd.add("--convert-to");
            cmd.add(convert);
            cmd.add("--outdir");
            cmd.add(workDir.toString());
            cmd.add(in.toString());

            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();

            String processOutput = new String(p.getInputStream().readAllBytes());
            int exit = p.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("LibreOffice conversion failed (exit=" + exit + ")\nOutput: " + processOutput + "\nCommand: " + String.join(" ", cmd));
            }

            // LibreOffice benennt die Datei nach Input-Basisname um: <name>.<ext>
            Path out = Path.of(workDir.toString(), in.getFileName().toString().replaceAll(".odt", "." + format.getSuffix()));
            if (!Files.exists(out)) {
                throw new IllegalStateException("LibreOffice did not produce expected file: " + out);
            }
            return Files.readAllBytes(out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("LibreOffice conversion interrupted", e);
        } finally {
            Files.deleteIfExists(in);
            try (var entries = Files.list(workDir)) {
                entries.forEach(f -> f.toFile().delete());
            }
            Files.deleteIfExists(workDir);
            deleteRecursively(userProfile);
        }
    }

    /** Loescht ein Verzeichnis samt Inhalt (best-effort; LibreOffice legt im Profil Unterordner an). */
    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
