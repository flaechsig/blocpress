package io.github.flaechsig.blocpress.server;

import io.github.flaechsig.blocpress.renderer.LibreOfficeExporter;
import io.github.flaechsig.blocpress.renderer.OutputFormat;
import io.github.flaechsig.blocpress.server.api.RenderApi;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class RenderApiImpl implements RenderApi {

    @Override
    public File renderDocument(File body, String accept) {
        if (body == null) {
            throw new BadRequestException("No content to render");
        }
        if (accept == null) {
            throw new BadRequestException("Accept-Header must be set to determine output format (application/pdf, application/rtf, application/vnd.oasis.opendocument.text)");
        }

        try {
            byte[] odtBytes = Files.readAllBytes(body.toPath());
            if (odtBytes.length == 0) {
                throw new BadRequestException("Uploaded template is empty.");
            }

            OutputFormat rendererFormat = switch (accept) {
                case "application/pdf" -> OutputFormat.PDF;
                case "application/rtf" -> OutputFormat.RTF;
                case "application/vnd.oasis.opendocument.text" -> OutputFormat.ODT;
                default -> throw new BadRequestException("Unsupported output format: " + accept);
            };

            byte[] transformed = LibreOfficeExporter.refreshAndTransform(odtBytes, rendererFormat);

            String suffix = "." + rendererFormat.toString();
            Path out = Files.createTempFile("blocpress_render_", suffix);
            out.toFile().deleteOnExit();
            Files.write(out, transformed);

            return out.toFile();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new InternalServerErrorException("Rendering failed.", e);
        }
    }
}
