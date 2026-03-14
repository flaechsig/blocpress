package io.github.flaechsig.blocpress.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flaechsig.blocpress.core.OutputFormat;
import io.github.flaechsig.blocpress.core.RenderEngine;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Polling-Worker der PENDING Render-Jobs aus der Datenbank holt und verarbeitet.
 * Nutzt SKIP LOCKED für nebenläufig-sicheres Claiming — mehrere Instanzen möglich.
 */
@ApplicationScoped
public class RenderJobWorker {

    private static final Logger LOG = LoggerFactory.getLogger(RenderJobWorker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ConfigProperty(name = "blocpress.async.result-retention", defaultValue = "PT24H")
    Duration resultRetention;

    @ConfigProperty(name = "blocpress.async.record-retention", defaultValue = "P7D")
    Duration recordRetention;

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

    /**
     * Löscht regelmäßig alte Job-Einträge in zwei Stufen:
     * 1. Ergebnis-Bytes nullen nach result-retention (Speicher freigeben, Datensatz bleibt)
     * 2. Gesamten Datensatz löschen nach record-retention
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void cleanupJobs() {
        LocalDateTime resultCutoff = LocalDateTime.now().minus(resultRetention);
        LocalDateTime recordCutoff = LocalDateTime.now().minus(recordRetention);

        int cleared = RenderJob.update(
                "result = null, updatedAt = ?1 WHERE result IS NOT NULL AND createdAt < ?2",
                LocalDateTime.now(), resultCutoff);
        if (cleared > 0) {
            LOG.info("Cleared result bytes from {} render jobs (older than {})", cleared, resultRetention);
        }

        long deleted = RenderJob.delete("createdAt < ?1", recordCutoff);
        if (deleted > 0) {
            LOG.info("Deleted {} old render job records (older than {})", deleted, recordRetention);
        }
    }

    /**
     * Legt einen Datensatz für einen synchronen Render-Aufruf an (Audit-Log).
     * Läuft in eigener Transaktion — Fehler hier beeinflussen die HTTP-Antwort nicht.
     * Das result-Feld bleibt null: der Aufrufer hat das Dokument bereits direkt erhalten.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordSync(String templateName, String dataJson, String outputType,
                           RenderJobStatus status, String errorMessage) {
        try {
            RenderJob job = new RenderJob();
            job.id = UUID.randomUUID();
            job.templateName = templateName;
            job.data = dataJson != null ? dataJson : "{}";
            job.outputType = outputType != null ? outputType : "pdf";
            job.status = status;
            job.errorMessage = errorMessage;
            job.persist();
        } catch (Exception e) {
            LOG.warn("Failed to record sync render job for template '{}': {}", templateName, e.getMessage());
        }
    }
}
