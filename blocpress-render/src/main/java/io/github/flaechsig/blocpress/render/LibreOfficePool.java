package io.github.flaechsig.blocpress.render;

import io.github.flaechsig.blocpress.core.LibreOfficeProcessor;
import io.github.flaechsig.blocpress.core.OutputFormat;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.document.DocumentFormat;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Verwaltet einen Pool persistenter LibreOffice-Instanzen für parallele Dokumentkonvertierung.
 *
 * <p>Beim Start werden {@code blocpress.libreoffice.workers} LibreOffice-Prozesse gestartet,
 * die jeweils auf einem eigenen UNO-Port lauschen (ab {@code blocpress.libreoffice.port-start}).
 * JodConverter verteilt eingehende Konvertierungsaufträge auf freie Instanzen.</p>
 *
 * <p>Ist LibreOffice nicht verfügbar, fällt der Pool auf den CLI-Modus zurück
 * ({@link LibreOfficeProcessor}).</p>
 */
@ApplicationScoped
public class LibreOfficePool {

    private static final Logger LOG = LoggerFactory.getLogger(LibreOfficePool.class);

    @ConfigProperty(name = "blocpress.libreoffice.home")
    Optional<String> officeHome;

    @ConfigProperty(name = "blocpress.libreoffice.workers", defaultValue = "2")
    int workers;

    @ConfigProperty(name = "blocpress.libreoffice.port-start", defaultValue = "2002")
    int portStart;

    private LocalOfficeManager manager;
    private boolean poolAvailable = false;

    void start(@Observes StartupEvent e) {
        int[] ports = IntStream.range(0, workers).map(i -> portStart + i).toArray();
        try {
            LocalOfficeManager.Builder builder = LocalOfficeManager.builder()
                    .portNumbers(ports)
                    .taskExecutionTimeout(120_000L)
                    .maxTasksPerProcess(200);
            officeHome.ifPresent(builder::officeHome);
            manager = builder.build();
            manager.start();
            poolAvailable = true;
            LOG.info("LibreOffice pool started: {} worker(s), ports {}-{}",
                    workers, portStart, portStart + workers - 1);
        } catch (Exception ex) {
            LOG.warn("LibreOffice pool unavailable, falling back to CLI mode: {}", ex.getMessage());
        }
    }

    void stop(@Observes ShutdownEvent e) {
        if (manager != null) {
            try {
                manager.stop();
                LOG.info("LibreOffice pool stopped");
            } catch (OfficeException ex) {
                LOG.warn("Error stopping LibreOffice pool: {}", ex.getMessage());
            }
        }
    }

    /**
     * Konvertiert ODT-Bytes in das gewünschte Ausgabeformat.
     * Nutzt den Pool falls verfügbar, sonst CLI-Fallback.
     */
    public byte[] convert(byte[] odtBytes, OutputFormat format) throws IOException {
        if (!poolAvailable) {
            return LibreOfficeProcessor.refreshAndTransform(odtBytes, format);
        }

        Path inputFile = Files.createTempFile("lo-in-", ".odt");
        Path outputFile = Files.createTempFile("lo-out-", "." + format.getSuffix());
        try {
            Files.write(inputFile, odtBytes);
            DocumentFormat sourceFormat = DefaultDocumentFormatRegistry.getFormatByExtension("odt");
            if (sourceFormat == null) {
                throw new IOException("ODT format not found in JodConverter registry");
            }
            DocumentFormat targetFormat = switch (format) {
                case PDF -> DefaultDocumentFormatRegistry.getFormatByExtension("pdf");
                case RTF -> DefaultDocumentFormatRegistry.getFormatByExtension("rtf");
                case ODT -> DefaultDocumentFormatRegistry.getFormatByExtension("odt");
            };
            if (targetFormat == null) {
                throw new IOException("Target format not found in JodConverter registry: " + format);
            }
            LocalConverter.make(manager)
                    .convert(inputFile.toFile())
                    .as(sourceFormat)
                    .to(outputFile.toFile())
                    .as(targetFormat)
                    .execute();
            return Files.readAllBytes(outputFile);
        } catch (OfficeException ex) {
            throw new IOException("LibreOffice conversion failed: " + ex.getMessage(), ex);
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }
}
