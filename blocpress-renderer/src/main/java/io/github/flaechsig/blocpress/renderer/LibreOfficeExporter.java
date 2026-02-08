package io.github.flaechsig.blocpress.renderer;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LibreOfficeExporter {
    /**
     * Refreshes and transforms document to specified output format
     */
    public static byte[] refreshAndTransform(byte[] input, @NonNull OutputFormat format) throws IOException {
        Path in = Files.createTempFile("blocpress_", ".odt");
        in.toFile().deleteOnExit();
        Files.write(in, input);

        Path workDir = Files.createTempDirectory("blocpress_out");
        Path out;
        try {
            var convert = switch (format) {
                case PDF -> "pdf";
                case RTF -> "rtf";
                case ODT -> "odt:writer8";
            };

            List<String> cmd = new ArrayList<>();
            cmd.add("libreoffice");
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

            int exit = p.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("LibreOffice conversion failed (exit=" + exit + ")\nCommand: " + String.join(" ", cmd));
            }

            // LibreOffice benennt die Datei nach Input-Basisname um: <name>.<ext>
            out = Path.of( workDir.toString(), in.getFileName().toString().replaceAll(".odt", "." + format.getSuffix()));
            if (!Files.exists(out)) {
                throw new IllegalStateException("LibreOffice did not produce expected file: " + out);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            Files.deleteIfExists(in);
        }

        return Files.readAllBytes(out);
    }
}
