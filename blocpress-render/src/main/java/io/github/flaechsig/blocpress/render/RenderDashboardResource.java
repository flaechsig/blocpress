package io.github.flaechsig.blocpress.render;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Dashboard-API für den Überblick über Render-Jobs.
 *
 * <ul>
 *   <li>{@code GET /api/render/dashboard}                 — Statistiken (Counts pro Status)</li>
 *   <li>{@code GET /api/render/dashboard/jobs}            — Letzte Jobs (alle Status, paginiert)</li>
 *   <li>{@code GET /api/render/dashboard/jobs?status=...} — Gefiltert nach Status</li>
 * </ul>
 */
@ApplicationScoped
@Path("/api/render/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class RenderDashboardResource {

    public record DashboardStats(
            long pending,
            long processing,
            long done,
            long failed,
            long total
    ) {}

    public record JobSummary(
            String id,
            String status,
            String templateName,
            String outputType,
            String createdAt,
            String updatedAt,
            String errorMessage
    ) {}

    @GET
    public DashboardStats getStats() {
        long pending = RenderJob.count("status", RenderJobStatus.PENDING);
        long processing = RenderJob.count("status", RenderJobStatus.PROCESSING);
        long done = RenderJob.count("status", RenderJobStatus.DONE);
        long failed = RenderJob.count("status", RenderJobStatus.FAILED);
        return new DashboardStats(pending, processing, done, failed, pending + processing + done + failed);
    }

    @GET
    @Path("jobs")
    public List<JobSummary> listJobs(
            @QueryParam("status") String status,
            @QueryParam("limit") Integer limit) {

        int pageSize = limit != null ? Math.min(limit, 200) : 50;
        List<RenderJob> jobs;
        if (status != null && !status.isBlank()) {
            RenderJobStatus s = RenderJobStatus.valueOf(status.toUpperCase());
            jobs = RenderJob.list("status ORDER BY createdAt DESC", s);
        } else {
            jobs = RenderJob.list("ORDER BY createdAt DESC");
        }
        return jobs.stream()
                .limit(pageSize)
                .map(j -> new JobSummary(
                        j.id.toString(),
                        j.status.name(),
                        j.templateName,
                        j.outputType,
                        j.createdAt.toString(),
                        j.updatedAt.toString(),
                        j.errorMessage))
                .toList();
    }
}
