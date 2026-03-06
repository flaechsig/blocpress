package io.github.flaechsig.blocpress.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flaechsig.blocpress.core.LibreOfficeProcessor;
import io.github.flaechsig.blocpress.core.OutputFormat;
import io.github.flaechsig.blocpress.core.RenderEngine;
import io.github.flaechsig.blocpress.render.model.RenderByNameRequest;
import io.github.flaechsig.blocpress.render.model.RenderRequest;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
 *
 * <p>Stellt zwei Endpunkte bereit:</p>
 * <ul>
 *   <li>{@code POST /api/render/template} — Stateless: Template direkt im Request (multipart oder JSON/Base64). Keine Authentifizierung erforderlich.</li>
 *   <li>{@code POST /api/render/{name}} — Template aus dem Production-Store. JWT erforderlich.</li>
 * </ul>
 */
@ApplicationScoped
@jakarta.ws.rs.Path("/api/render")
public class RenderResource {
    private final static Logger logger = LoggerFactory.getLogger(RenderResource.class);
    private final static ObjectMapper mapper = new ObjectMapper();

    @Inject
    TemplateCache templateCache;

    @POST
    @jakarta.ws.rs.Path("/template")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces({"application/pdf", "application/rtf", "application/vnd.oasis.opendocument.text"})
    @PermitAll
    public File renderDocumentMultipart(
            @HeaderParam("Accept") String accept,
            @FormParam("template") InputStream templateInputStream,
            @FormParam("data") String data) {
        logger.info("Generating document from multipart template upload");
        OutputFormat format = switch (accept) {
            case "application/vnd.oasis.opendocument.text" -> ODT;
            case "application/pdf" -> OutputFormat.PDF;
            case "application/rtf" -> RTF;
            default -> throw new WebApplicationException(
                    "Unsupported Accept header: " + accept, Response.Status.NOT_ACCEPTABLE);
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

    @POST
    @jakarta.ws.rs.Path("/template")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({"application/pdf", "application/rtf", "application/vnd.oasis.opendocument.text"})
    @PermitAll
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

    @POST
    @jakarta.ws.rs.Path("/{name}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({"application/pdf", "application/rtf", "application/vnd.oasis.opendocument.text"})
    public File renderDocumentByName(
            @PathParam("name") String name,
            RenderByNameRequest renderByNameRequest) {
        logger.info("Rendering document from template name: {}", name);
        try {
            OutputFormat format = switch (renderByNameRequest.getOutputType().toString().toLowerCase()) {
                case "pdf" -> OutputFormat.PDF;
                case "rtf" -> RTF;
                case "odt" -> ODT;
                default -> throw new IllegalArgumentException(
                        "Invalid output type: " + renderByNameRequest.getOutputType());
            };
            var dataNode = mapper.valueToTree(renderByNameRequest.getData());
            byte[] templateContent = templateCache.getTemplateContentByName(name);
            Path tempFile = Files.createTempFile("template-" + name, ".odt");
            Files.write(tempFile, templateContent);
            return mergeAndTransform(tempFile, dataNode, format);
        } catch (TemplateNotFoundException e) {
            logger.warn("Template not found or not approved: {}", name);
            if (e.getMessage().contains("not approved")) {
                throw new WebApplicationException(e.getMessage(), Response.Status.FORBIDDEN);
            } else {
                throw new WebApplicationException(e.getMessage(), Response.Status.NOT_FOUND);
            }
        } catch (IOException e) {
            logger.error("Failed to fetch or render template {}: {}", name, e.getMessage(), e);
            throw new WebApplicationException("Failed to render document: " + e.getMessage(),
                    Response.Status.INTERNAL_SERVER_ERROR);
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
