package io.github.flaechsig.blocpress.server.render.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flaechsig.blocpress.renderer.LibreOfficeExporter;
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

public class TemplateResource implements TemplateApi {
    private static Logger logger = LoggerFactory.getLogger(TemplateResource.class);
    private static ObjectMapper mapper = new ObjectMapper();

    @Override
    public File mergeTemplate(String accept, InputStream templateInputStream, String data) {
        logger.info("Merging template");
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
            var result = LibreOfficeExporter.refreshAndTransform(merge,format);
            logger.info("Build output");
            output = Files.createTempFile("output",format.getSuffix());
            Files.write(output, result);
            return output.toFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}