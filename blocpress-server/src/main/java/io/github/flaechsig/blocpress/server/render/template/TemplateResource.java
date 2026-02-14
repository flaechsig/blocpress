package io.github.flaechsig.blocpress.server.render.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flaechsig.blocpress.renderer.LibreOfficeProcessor;
import io.github.flaechsig.blocpress.renderer.OutputFormat;
import io.github.flaechsig.blocpress.renderer.RenderEngine;
import io.github.flaechsig.blocpress.server.api.TemplateApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static io.github.flaechsig.blocpress.renderer.OutputFormat.ODT;
import static io.github.flaechsig.blocpress.renderer.OutputFormat.RTF;

/**
 * REST-Endpoint zur Dokumentgenerierung aus einem ODT-Template und JSON-Daten.
 * Implementiert {@code POST /api/template/generate}.
 *
 * <p><b>Design-Referenzen:</b></p>
 * <ul>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-ti-1">TI-1: REST-API</a></li>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-tf-5">TF-5: Dokument generieren</a></li>
 *   <li>EDC: <a href="docs/Element_Design_Concept.adoc#edc-uc-1">UC-1: Template hochladen</a></li>
 * </ul>
 */
public class TemplateResource implements TemplateApi {
    private static Logger logger = LoggerFactory.getLogger(TemplateResource.class);
    private static ObjectMapper mapper = new ObjectMapper();

    @Override
    public File generateDocument(String accept, InputStream templateInputStream, String data) {
        logger.info("Generating document from template");
        OutputFormat format = switch (accept) {
            case "application/vnd.oasis.opendocument.text" -> ODT;
            case "application/pdf" -> OutputFormat.PDF;
            case "application/rtf" -> RTF;
            default -> throw new IllegalStateException("Unexpected value: " + accept);
        };
        Path tempFile = null;
        Path output = null;
        try {
            tempFile = Files.createTempFile("template", ".odt");
            Files.copy(templateInputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            var odt = tempFile.toUri().toURL();
            var json = mapper.readTree(data);
            logger.info("Calling merge");
            var merge = RenderEngine.mergeTemplate(odt, json);
            logger.info("Calling transform");
            var result = LibreOfficeProcessor.refreshAndTransform(merge,format);
            logger.info("Build output");
            output = Files.createTempFile("output",format.getSuffix());
            Files.write(output, result);
            return output.toFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}