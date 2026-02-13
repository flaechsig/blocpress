package io.github.flaechsig.blocpress.server.render.render;

import io.github.flaechsig.blocpress.renderer.LibreOfficeExporter;
import io.github.flaechsig.blocpress.renderer.OutputFormat;
import io.github.flaechsig.blocpress.server.api.RenderApi;
import jakarta.ws.rs.WebApplicationException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RenderHandler implements RenderApi {

    @Override
    public File renderDocument(String accept, File body) {
        try {
            byte[] odtBytes = Files.readAllBytes(body.toPath());
            if (odtBytes.length == 0) {
                throw new WebApplicationException( "Uploaded template is empty.", 400);
            }

            OutputFormat rendererFormat = switch (accept) {
                case "application/pdf" -> OutputFormat.PDF;
                case "application/rtf" -> OutputFormat.RTF;
                case "application/vnd.oasis.opendocument.text" -> OutputFormat.ODT;
                default -> throw new WebApplicationException( "Unsupported output format: " + accept, 415);
            };

            byte[] transformed = LibreOfficeExporter.refreshAndTransform(odtBytes, rendererFormat);

            String suffix = "." + rendererFormat;
            Path out = Files.createTempFile("blocpress_render_", suffix);
            Files.write(out, transformed);

            return out.toFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
