package io.github.flaechsig.blocpress.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flaechsig.blocpress.core.OutputFormat;
import io.github.flaechsig.blocpress.core.RenderEngine;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Polling-Worker der PENDING Render-Jobs aus der Datenbank holt und verarbeitet.
 * Nutzt SKIP LOCKED für nebenläufig-sicheres Claiming — mehrere Instanzen möglich.
 */
@ApplicationScoped
public class RenderJobWorker {

    private static final Logger LOG = LoggerFactory.getLogger(RenderJobWorker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    LibreOfficePool libreOfficePool;

    @Inject
    TemplateCache templateCache;

    @Inject
    WebhookSender webhookSender;

    @Scheduled(every = "${blocpress.async.poll-interval:2s}")
    @Transactional
    public void processNextJob() {
        RenderJob job = RenderJob.claimNextPending();
        if (job == null) {
            return;
        }
        LOG.info("Processing render job {} (template={}, format={})", job.id, job.templateName, job.outputType);
        try {
            OutputFormat format = switch (job.outputType.toLowerCase()) {
                case "pdf" -> OutputFormat.PDF;
                case "rtf" -> OutputFormat.RTF;
                default -> OutputFormat.ODT;
            };

            byte[] templateContent = templateCache.getTemplateContentByName(job.templateName);
            Path tempFile = Files.createTempFile("async-job-" + job.id, ".odt");
            Files.write(tempFile, templateContent);

            var json = MAPPER.readTree(job.data);
            var odt = tempFile.toUri().toURL();
            byte[] merged = RenderEngine.mergeTemplate(odt, json);
            byte[] result = libreOfficePool.convert(merged, format);

            Files.deleteIfExists(tempFile);

            job.result = result;
            job.status = RenderJobStatus.DONE;
            job.updatedAt = LocalDateTime.now();
            job.persist();

            LOG.info("Render job {} completed ({} bytes)", job.id, result.length);
            webhookSender.sendAsync(job.webhookUrl, job.id, RenderJobStatus.DONE);

        } catch (Exception e) {
            LOG.error("Render job {} failed: {}", job.id, e.getMessage(), e);
            job.status = RenderJobStatus.FAILED;
            job.errorMessage = e.getMessage();
            job.updatedAt = LocalDateTime.now();
            job.persist();
            webhookSender.sendAsync(job.webhookUrl, job.id, RenderJobStatus.FAILED);
        }
    }
}
