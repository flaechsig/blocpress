package io.github.flaechsig.blocpress.render.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flaechsig.blocpress.core.LibreOfficeProcessor;
import io.github.flaechsig.blocpress.core.OutputFormat;
import io.github.flaechsig.blocpress.core.RenderEngine;
import io.github.flaechsig.blocpress.render.api.TemplateApi;
import io.github.flaechsig.blocpress.render.model.RenderRequest;
import io.quarkus.security.Authenticated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static io.github.flaechsig.blocpress.core.OutputFormat.ODT;
import static io.github.flaechsig.blocpress.core.OutputFormat.RTF;

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
@Authenticated
public class TemplateResource implements TemplateApi {
    private final static Logger logger = LoggerFactory.getLogger(TemplateResource.class);
    private final static ObjectMapper mapper = new ObjectMapper();

    @Override
    public File renderDocumentMultipart(String accept, InputStream templateInputStream, String data) {
        logger.info("Generating document from template");
        OutputFormat format = switch (accept) {
            case "application/vnd.oasis.opendocument.text" -> ODT;
            case "application/pdf" -> OutputFormat.PDF;
            case "application/rtf" -> RTF;
            default -> throw new IllegalStateException("Unexpected value: " + accept);
        };
        try {
            Path tempFile = Files.createTempFile("template", ".odt");
            Files.copy(templateInputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            var json = mapper.readTree(data);
            return mergeAndTransform(tempFile, json, format);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public File renderDocumentJson(RenderRequest renderRequest) {
        logger.info("Rendering document from base64-encoded template");
        OutputFormat format = switch (renderRequest.getOutputType()) {
            case PDF -> OutputFormat.PDF;
            case RTF -> RTF;
            case ODT -> ODT;
        };
        try {
            Path tempFile = Files.createTempFile("template", ".odt");
            Files.write(tempFile, renderRequest.getTemplate());
            var json = mapper.valueToTree(renderRequest.getData());
            return mergeAndTransform(tempFile, json, format);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private File mergeAndTransform(Path templatePath, JsonNode json, OutputFormat format) throws IOException {
        var odt = templatePath.toUri().toURL();
        logger.info("Calling merge");
        var merge = RenderEngine.mergeTemplate(odt, json);
        logger.info("Calling transform");
        var result = LibreOfficeProcessor.refreshAndTransform(merge, format);
        logger.info("Build output");
        Path output = Files.createTempFile("output", format.getSuffix());
        Files.write(output, result);
        return output.toFile();
    }

}