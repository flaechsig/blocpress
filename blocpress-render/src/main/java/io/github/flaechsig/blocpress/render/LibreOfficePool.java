package io.github.flaechsig.blocpress.render;

import io.github.flaechsig.blocpress.core.OutputFormat;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Warme LibreOffice-Instanzen, eine je Worker (ADR-0021).
 *
 * <p>Beim Start legt der Pool so viele Instanzen an, wie CPU-Kontingent und Speicherlimit
 * erlauben ({@link WorkerCount}, REQ-0027, REQ-0103), und meldet sich erst bereit, wenn alle
 * laufen (REQ-0098). Jede Konvertierung bekommt eine freie Instanz und laeuft in ihr, ohne
 * LibreOffice neu zu starten (REQ-0099). Eine Instanz wird ersetzt, wenn sie das Zeitlimit
 * ueberschreitet (REQ-0100, REQ-0104), abstuerzt (REQ-0102) oder ihre Nutzungs- oder
 * Speichergrenze erreicht (REQ-0101); ihr Platz bleibt so lange belegt.</p>
 *
 * <p>Ohne CDI ({@code new LibreOfficePool()}) gelten die Standardwerte; die Instanzen starten
 * dann erst bei Bedarf, bis zur Worker-Zahl.</p>
 */
@ApplicationScoped
public class LibreOfficePool {

    private static final Logger LOG = LoggerFactory.getLogger(LibreOfficePool.class);
    private static final long MIB = 1024L * 1024L;

    /** Obergrenze ({@code BLOCPRESS_LO_WORKERS}); leer, wenn nicht gesetzt. */
    @ConfigProperty(name = "blocpress.libreoffice.workers")
    Optional<Integer> maxWorkers = Optional.empty();

    @ConfigProperty(name = "blocpress.libreoffice.idle-timeout", defaultValue = "30s")
    Duration idleTimeout = Duration.ofSeconds(30);

    @ConfigProperty(name = "blocpress.libreoffice.max-duration", defaultValue = "10m")
    Duration maxDuration = Duration.ofMinutes(10);

    @ConfigProperty(name = "blocpress.libreoffice.start-timeout", defaultValue = "60s")
    Duration startTimeout = Duration.ofSeconds(60);

    @ConfigProperty(name = "blocpress.libreoffice.max-conversions", defaultValue = "500")
    int maxConversions = 500;

    @ConfigProperty(name = "blocpress.libreoffice.max-memory-mb", defaultValue = "512")
    long maxMemoryMb = 512;

    /** Grundbedarf von render ohne Instanzen; leer = eigener Speicher beim Start plus 128 MiB. */
    @ConfigProperty(name = "blocpress.libreoffice.memory-base-mb")
    Optional<Long> memoryBaseMb = Optional.empty();

    @ConfigProperty(name = "blocpress.libreoffice.memory-per-instance-mb", defaultValue = "350")
    long memoryPerInstanceMb = 350;

    /** Befehl fuer den Helfer, kommagetrennt; leer = mitgelieferter {@code bp-convert.py}. */
    @ConfigProperty(name = "blocpress.libreoffice.helper")
    Optional<List<String>> helperCommand = Optional.empty();

    private final BlockingQueue<LibreOfficeInstance> free = new LinkedBlockingQueue<>();
    private final List<LibreOfficeInstance> instances = new ArrayList<>();
    private final ExecutorService maintenance = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "libreoffice-maintenance");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean started;
    /** Alle Instanzen liefen schon einmal; danach bleibt der Pool bereit, waehrend einzelne ersetzt werden. */
    private volatile boolean allStarted;

    void onStart(@Observes StartupEvent event) {
        start();
    }

    void onStop(@Observes ShutdownEvent event) {
        stop();
    }

    /** Zahl der Instanzen: CPU-Kontingent und Speicherlimit, hoechstens die Einstellung. */
    public int workers() {
        long base = memoryBaseMb.map(mb -> mb * MIB).orElseGet(() -> ownRssBytes() + 128 * MIB);
        return WorkerCount.resolve(maxWorkers, base, memoryPerInstanceMb * MIB);
    }

    /** Startet alle Instanzen (idempotent). */
    public synchronized void start() {
        if (started) {
            return;
        }
        List<String> command = command();
        int count = workers();
        for (int i = 0; i < count; i++) {
            LibreOfficeInstance instance = new LibreOfficeInstance(command);
            instances.add(instance);
            try {
                instance.start(startTimeout);
                free.add(instance);
            } catch (IOException e) {
                LOG.error("LibreOffice instance {} did not start: {}", i, e.getMessage());
                retryUntilStarted(instance);
            }
        }
        started = true;
        LOG.info("LibreOffice pool started: {} warm instance(s) (limit {}), idle timeout {}, max duration {}",
                count, maxWorkers.map(String::valueOf).orElse("none"), idleTimeout, maxDuration);
    }

    /** Beendet alle Instanzen. */
    public synchronized void stop() {
        started = false;
        allStarted = false;
        instances.forEach(LibreOfficeInstance::destroy);
        instances.clear();
        free.clear();
    }

    /**
     * Bereit, sobald alle Instanzen einmal liefen (REQ-0098). Wird danach eine Instanz ersetzt,
     * bleibt der Pool bereit, solange mindestens eine laeuft.
     */
    public synchronized boolean ready() {
        if (!started || instances.isEmpty()) {
            return false;
        }
        if (!allStarted && instances.stream().allMatch(LibreOfficeInstance::isAlive)) {
            allStarted = true;
        }
        return allStarted && instances.stream().anyMatch(LibreOfficeInstance::isAlive);
    }

    /** Laufende Instanzen, fuer Readiness und Tests. */
    synchronized List<LibreOfficeInstance> instances() {
        return List.copyOf(instances);
    }

    /**
     * Konvertiert ODT-Bytes in einer freien, laufenden Instanz. Wartet, bis eine frei ist.
     */
    public byte[] convert(byte[] odtBytes, OutputFormat format) throws IOException {
        LibreOfficeInstance instance = free.poll();
        if (instance == null) {
            instance = grow();
        }
        try {
            if (instance == null) {
                instance = free.poll(maxDuration.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for a LibreOffice instance", e);
        }
        if (instance == null) {
            throw new IOException("No LibreOffice instance available within " + maxDuration.toSeconds() + " s");
        }
        try {
            if (!instance.isAlive()) {
                instance.start(startTimeout);
            }
            return instance.convert(odtBytes, format, idleTimeout, maxDuration);
        } finally {
            release(instance);
        }
    }

    /**
     * Legt eine weitere Instanz an, solange weniger als {@link #workers()} existieren (Nutzung ohne
     * Start beim Hochfahren). Die neue Instanz startet beim Konvertieren.
     */
    private synchronized LibreOfficeInstance grow() {
        if (started && instances.size() >= workers()) {
            return null;
        }
        if (!started) {
            started = true;
            allStarted = true;
        }
        if (instances.size() >= workers()) {
            return null;
        }
        LibreOfficeInstance instance = new LibreOfficeInstance(command());
        instances.add(instance);
        return instance;
    }

    /** Gibt die Instanz zurueck; muss sie ersetzt werden, erst danach (REQ-0101, REQ-0102). */
    private void release(LibreOfficeInstance instance) {
        String reason = replacementReason(instance);
        if (reason == null) {
            free.add(instance);
            return;
        }
        maintenance.execute(() -> {
            LOG.info("Replacing LibreOffice instance (helper pid {}): {}", instance.pid(), reason);
            try {
                instance.start(startTimeout);
            } catch (IOException e) {
                LOG.error("LibreOffice instance could not be replaced: {}", e.getMessage());
            } finally {
                free.add(instance);
            }
        });
    }

    /** Startet eine Instanz, die beim Hochfahren nicht startete, im Hintergrund erneut (alle 10 s). */
    private void retryUntilStarted(LibreOfficeInstance instance) {
        maintenance.execute(() -> {
            while (started || !allStarted) {
                try {
                    Thread.sleep(10_000);
                    if (!started) {
                        return;
                    }
                    instance.start(startTimeout);
                    free.add(instance);
                    return;
                } catch (IOException e) {
                    LOG.error("LibreOffice instance still not starting: {}", e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
    }

    private String replacementReason(LibreOfficeInstance instance) {
        if (!instance.isAlive()) {
            return "not running";
        }
        if (instance.conversions() >= maxConversions) {
            return instance.conversions() + " conversions";
        }
        long rss = instance.rssBytes();
        if (rss > maxMemoryMb * MIB) {
            return (rss / MIB) + " MiB resident";
        }
        return null;
    }

    /** Resident Set Size dieses Prozesses (JVM oder Native Image), aus {@code /proc/self/status}. */
    private static long ownRssBytes() {
        try (var lines = Files.lines(Path.of("/proc/self/status"))) {
            return lines.filter(l -> l.startsWith("VmRSS:")).findFirst()
                    .map(l -> Long.parseLong(l.replaceAll("\\D", "")) * 1024).orElse(256 * MIB);
        } catch (IOException | RuntimeException e) {
            return 256 * MIB;
        }
    }

    private List<String> command() {
        return helperCommand.filter(c -> !c.isEmpty()).orElseGet(() -> List.of("python3", helperScript().toString()));
    }

    /** Legt den mitgelieferten Helfer im Arbeitsverzeichnis ab. */
    private static Path helperScript() {
        Path dir = Path.of(System.getProperty("java.io.tmpdir", "/tmp"), "blocpress");
        Path script = dir.resolve("bp-convert.py");
        try (InputStream in = LibreOfficePool.class.getResourceAsStream("/libreoffice/bp-convert.py")) {
            if (in == null) {
                throw new IllegalStateException("libreoffice/bp-convert.py missing from the classpath");
            }
            Files.createDirectories(dir);
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
            return script;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write LibreOffice helper to " + script, e);
        }
    }
}
