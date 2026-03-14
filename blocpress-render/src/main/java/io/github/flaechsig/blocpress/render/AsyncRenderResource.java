package io.github.flaechsig.blocpress.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * REST-API für asynchrones Rendering.
 *
 * <ul>
 *   <li>{@code POST /api/render/jobs}        — Job einreihen, gibt 202 + Job-URL zurück</li>
 *   <li>{@code GET  /api/render/jobs/{id}}   — Job-Status abfragen</li>
 *   <li>{@code GET  /api/render/jobs/{id}/result} — Ergebnis herunterladen (nur DONE)</li>
 * </ul>
 */
@ApplicationScoped
@Path("/api/render/jobs")
public class AsyncRenderResource {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncRenderResource.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record JobRequest(
            String templateName,
            Object data,
            String outputType,
            String webhookUrl
    ) {}

    public record JobStatus(
            UUID id,
            String status,
            String templateName,
            String outputType,
            String webhookUrl,
            String createdAt,
            String updatedAt,
            String errorMessage,
            String resultUrl
    ) {}

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response submitJob(JobRequest request) {
        if (request.templateName() == null || request.templateName().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"templateName is required\"}")
                    .build();
        }
        if (request.data() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"data is required\"}")
                    .build();
        }

        try {
            JsonNode dataNode = MAPPER.valueToTree(request.data());

            RenderJob job = new RenderJob();
            job.id = UUID.randomUUID();
            job.templateName = request.templateName();
            job.data = MAPPER.writeValueAsString(dataNode);
            job.outputType = request.outputType() != null ? request.outputType().toLowerCase() : "pdf";
            job.webhookUrl = request.webhookUrl();
            job.persist();

            LOG.info("Render job {} submitted (template={}, format={})", job.id, job.templateName, job.outputType);
            return Response.accepted(toStatus(job)).build();

        } catch (Exception e) {
            LOG.error("Failed to submit render job: {}", e.getMessage(), e);
            return Response.serverError()
                    .entity("{\"error\":\"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getJobStatus(@PathParam("id") UUID id) {
        RenderJob job = RenderJob.findById(id);
        if (job == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(toStatus(job)).build();
    }

    @GET
    @Path("{id}/result")
    public Response getJobResult(@PathParam("id") UUID id) {
        RenderJob job = RenderJob.findById(id);
        if (job == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (job.status != RenderJobStatus.DONE) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("{\"status\":\"" + job.status + "\",\"message\":\"Result not ready yet\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        String mimeType = switch (job.outputType.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "rtf" -> "application/rtf";
            default -> "application/vnd.oasis.opendocument.text";
        };
        return Response.ok(job.result)
                .header("Content-Type", mimeType)
                .header("Content-Disposition", "attachment; filename=\"result." + job.outputType + "\"")
                .build();
    }

    private JobStatus toStatus(RenderJob job) {
        String resultUrl = job.status == RenderJobStatus.DONE
                ? "/api/render/jobs/" + job.id + "/result"
                : null;
        return new JobStatus(
                job.id,
                job.status.name(),
                job.templateName,
                job.outputType,
                job.webhookUrl,
                job.createdAt.toString(),
                job.updatedAt.toString(),
                job.errorMessage,
                resultUrl
        );
    }
}
