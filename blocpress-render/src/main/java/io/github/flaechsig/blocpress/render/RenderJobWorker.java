package io.github.flaechsig.blocpress.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flaechsig.blocpress.core.OutputFormat;
import io.github.flaechsig.blocpress.core.RenderEngine;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Polling-Worker der PENDING Render-Jobs aus der Datenbank holt und verarbeitet — bis zu
 * so viele parallel je Instanz, wie LibreOffice-Worker laufen ({@link LibreOfficePool#workers()}), bis die Warteschlange leer ist.
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
    ProductionTextBlocks productionTextBlocks;

    @Inject
    WebhookSender webhookSender;

    @Inject
    RenderLocaleConfig localeConfig;

    /** Parallele Job-Verarbeitung je Instanz — gleich der LibreOffice-Worker-Zahl (der Engpass). */
    int workers;

    /** PROCESSING-Jobs, die laenger haengen (Instanz abgestuerzt), gehen zurueck auf PENDING. */
    @ConfigProperty(name = "blocpress.async.stale-after", defaultValue = "PT10M")
    Duration staleAfter;

    private final AtomicInteger activeLoops = new AtomicInteger();
    private ExecutorService executor;

    @PostConstruct
    void start() {
        workers = libreOfficePool.workers();
        executor = Executors.newFixedThreadPool(workers);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    /**
     * Verteiler: startet bis zu {@code workers} Verarbeitungsschleifen. Jede Schleife holt Jobs,
     * bis die Warteschlange leer ist — ein fertiger Job zieht sofort den naechsten nach. Das
     * Poll-Intervall bestimmt damit nur noch, wie schnell ein neuer Job nach einer Leerlaufphase
     * startet, nicht mehr den Durchsatz (bis 2.6.1: genau ein Job je Takt, max. ~0,5 Jobs/s).
     */
    @Scheduled(every = "${blocpress.async.poll-interval:2s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void dispatch() {
        while (activeLoops.get() < workers) {
            activeLoops.incrementAndGet();
            executor.submit(this::drainQueue);
        }
    }

    /** Holt und verarbeitet Jobs, bis keiner mehr wartet. */
    void drainQueue() {
        try {
            RenderJob job;
            while ((job = QuarkusTransaction.requiringNew().call(RenderJob::claimNextPending)) != null) {
                process(job.id, job.templateName, job.outputType, job.data, job.webhookUrl);
            }
        } catch (Exception e) {
            LOG.error("Job-Verarbeitung abgebrochen: {}", e.getMessage(), e);
        } finally {
            activeLoops.decrementAndGet();
        }
    }

    /** Rendert ausserhalb einer Transaktion; nur das Speichern des Ergebnisses ist transaktional. */
    private void process(UUID id, String templateName, String outputType, String data, String webhookUrl) {
        LOG.info("Processing render job {} (template={}, format={})", id, templateName, outputType);
        try {
            OutputFormat format = switch (outputType.toLowerCase()) {
                case "pdf" -> OutputFormat.PDF;
                case "rtf" -> OutputFormat.RTF;
                default -> OutputFormat.ODT;
            };

            // DB-Zugriff braucht eine Transaktion (der Worker-Thread hat keinen Request-Kontext)
            byte[] templateContent = QuarkusTransaction.requiringNew()
                    .call(() -> templateCache.getTemplateContentByName(templateName));
            Path tempFile = Files.createTempFile("async-job-" + id, ".odt");
            byte[] result;
            try {
                Files.write(tempFile, templateContent);
                var json = MAPPER.readTree(data);
                byte[] merged = RenderEngine.mergeTemplate(tempFile.toUri().toURL(), json, localeConfig.defaultLocale(),
                        productionTextBlocks.resolver());
                result = libreOfficePool.convert(merged, format);
            } finally {
                Files.deleteIfExists(tempFile);
            }

            finish(id, RenderJobStatus.DONE, result, null);
            LOG.info("Render job {} completed ({} bytes)", id, result.length);
            webhookSender.sendAsync(webhookUrl, id, RenderJobStatus.DONE);

        } catch (Exception e) {
            LOG.error("Render job {} failed: {}", id, e.getMessage(), e);
            finish(id, RenderJobStatus.FAILED, null, e.getMessage());
            webhookSender.sendAsync(webhookUrl, id, RenderJobStatus.FAILED);
        }
    }

    private void finish(UUID id, RenderJobStatus status, byte[] result, String errorMessage) {
        QuarkusTransaction.requiringNew().run(() -> {
            RenderJob job = RenderJob.findById(id);
            if (job == null) {
                return; // inzwischen aufgeraeumt
            }
            job.result = result;
            job.status = status;
            job.errorMessage = errorMessage;
            job.updatedAt = LocalDateTime.now();
        });
    }

    /**
     * Holen und Rendern laufen in getrennten Transaktionen: stirbt eine Instanz mitten im
     * Rendern, bliebe der Job sonst fuer immer PROCESSING. Solche Jobs werden wieder PENDING.
     */
    @Scheduled(every = "1m")
    @Transactional
    void requeueStaleJobs() {
        int requeued = RenderJob.update("status = ?1, updatedAt = ?2 WHERE status = ?3 AND updatedAt < ?4",
                RenderJobStatus.PENDING, LocalDateTime.now(), RenderJobStatus.PROCESSING,
                LocalDateTime.now().minus(staleAfter));
        if (requeued > 0) {
            LOG.warn("{} haengende Render-Jobs (PROCESSING > {}) wieder auf PENDING gesetzt", requeued, staleAfter);
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
