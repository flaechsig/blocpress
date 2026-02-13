package io.github.flaechsig.blocpress.server.render.render;

import io.github.flaechsig.blocpress.renderer.LibreOfficeExporter;
import io.github.flaechsig.blocpress.renderer.OutputFormat;
import io.github.flaechsig.blocpress.server.api.RenderApi;
import jakarta.ws.rs.WebApplicationException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * REST-Endpoint zur Konvertierung von bereits zusammengeführten ODT-Dokumenten
 * in andere Formate (PDF, RTF, ODT). Implementiert {@code POST /render}.
 *
 * <p><b>Design-Referenzen:</b></p>
 * <ul>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-ti-1">TI-1: REST-API</a></li>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-uc-10">UC-10: Testdokument mit Template generieren</a></li>
 * </ul>
 */
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
