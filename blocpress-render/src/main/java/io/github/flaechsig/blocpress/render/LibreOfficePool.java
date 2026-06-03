package io.github.flaechsig.blocpress.render;

import io.github.flaechsig.blocpress.core.LibreOfficeProcessor;
import io.github.flaechsig.blocpress.core.OutputFormat;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * Begrenzt die Nebenläufigkeit der LibreOffice-Dokumentkonvertierung.
 *
 * <p>Die Konvertierung läuft über einen externen {@code soffice}-Prozess
 * ({@link LibreOfficeProcessor}), der pro Aufruf ein eigenes LibreOffice-Profil nutzt
 * und damit nebenläufigkeitssicher ist. Diese Klasse drosselt die Zahl gleichzeitig
 * laufender Konvertierungen über eine {@link Semaphore} auf
 * {@code blocpress.libreoffice.workers}, um Speicher/CPU zu begrenzen.</p>
 *
 * <p>Der frühere JodConverter/UNO-In-Process-Pool wurde entfernt: Die OpenOffice-UNO-Jars
 * sind versiegelt und nicht mit GraalVM-Native-Image kompatibel. Der CLI-Pfad erlaubt den
 * nativen Build und bildet die Nebenläufigkeit über dieses Throttling nach.</p>
 */
@ApplicationScoped
public class LibreOfficePool {

    private static final Logger LOG = LoggerFactory.getLogger(LibreOfficePool.class);
    private static final int DEFAULT_WORKERS = 2;

    @ConfigProperty(name = "blocpress.libreoffice.workers", defaultValue = "2")
    int workers;

    private volatile Semaphore slots;

    /** Lazy-Initialisierung, damit die Klasse auch ohne CDI ({@code new LibreOfficePool()}) nutzbar ist. */
    private Semaphore slots() {
        Semaphore s = slots;
        if (s == null) {
            synchronized (this) {
                s = slots;
                if (s == null) {
                    int permits = workers > 0 ? workers : DEFAULT_WORKERS;
                    s = new Semaphore(permits, true);
                    slots = s;
                    LOG.info("LibreOffice CLI throttle initialised: {} parallel conversion(s)", permits);
                }
            }
        }
        return s;
    }

    /**
     * Konvertiert ODT-Bytes in das gewünschte Ausgabeformat über den LibreOffice-CLI-Pfad.
     * Die Zahl gleichzeitiger Konvertierungen ist auf {@code workers} begrenzt.
     */
    public byte[] convert(byte[] odtBytes, OutputFormat format) throws IOException {
        Semaphore s = slots();
        try {
            s.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for a LibreOffice conversion slot", e);
        }
        try {
            return LibreOfficeProcessor.refreshAndTransform(odtBytes, format);
        } finally {
            s.release();
        }
    }
}
