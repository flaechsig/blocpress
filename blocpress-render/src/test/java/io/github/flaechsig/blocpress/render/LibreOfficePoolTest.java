package io.github.flaechsig.blocpress.render;

import io.github.flaechsig.blocpress.core.OutputFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verwaltung der warmen Instanzen (ADR-0021) mit einer Attrappe des Helfers
 * ({@code fake-helper.py}): Zeitlimits, Absturz, Ersetzen und Bereitschaft, ohne LibreOffice.
 */
class LibreOfficePoolTest {

    private static final String FAKE = Path.of("src/test/resources/libreoffice/fake-helper.py").toAbsolutePath().toString();

    private LibreOfficePool pool;

    @AfterEach
    void stop() {
        if (pool != null) {
            pool.stop();
        }
    }

    private LibreOfficePool pool(String... args) {
        pool = new LibreOfficePool();
        pool.maxWorkers = Optional.of(1);
        pool.startTimeout = Duration.ofSeconds(5);
        var command = new java.util.ArrayList<>(List.of("python3", FAKE));
        command.addAll(List.of(args));
        pool.helperCommand = Optional.of(command);
        return pool;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private long helperPid() {
        return pool.instances().get(0).pid();
    }

    @Test
    @DisplayName("REQ-0100: conversionWithoutProgressIsAbortedAndTheInstanceReplaced")
    void conversionWithoutProgressIsAbortedAndTheInstanceReplaced() throws Exception {
        pool("ok");
        pool.idleTimeout = Duration.ofMillis(300);
        pool.convert(bytes("first"), OutputFormat.PDF);
        long before = helperPid();

        long start = System.nanoTime();
        var e = assertThrows(LibreOfficeTimeoutException.class, () -> pool.convert(bytes("HANG"), OutputFormat.PDF));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(e.getMessage().contains("no progress for 300 ms"), e.getMessage());
        assertTrue(millis >= 300 && millis < 3000, "aborted after " + millis + " ms");
        assertArrayEquals(bytes("next"), pool.convert(bytes("next"), OutputFormat.PDF));
        assertNotEquals(before, helperPid(), "instance was not replaced");
    }

    @Test
    @DisplayName("REQ-0100: progressRestartsTheIdleTime")
    void progressRestartsTheIdleTime() throws Exception {
        pool("ok");
        pool.idleTimeout = Duration.ofMillis(300);
        pool.maxDuration = Duration.ofMillis(1200);
        // SPIN meldet alle 50 ms Fortschritt: das Leerlauflimit greift nicht, erst die Hoechstdauer
        var e = assertThrows(LibreOfficeTimeoutException.class, () -> pool.convert(bytes("SPIN"), OutputFormat.PDF));
        assertTrue(e.getMessage().contains("maximum duration"), e.getMessage());
    }

    @Test
    @DisplayName("REQ-0104: conversionLongerThanTheMaximumDurationIsAbortedAndTheInstanceReplaced")
    void conversionLongerThanTheMaximumDurationIsAbortedAndTheInstanceReplaced() throws Exception {
        pool("ok");
        pool.idleTimeout = Duration.ofSeconds(5);
        pool.maxDuration = Duration.ofSeconds(1);
        pool.convert(bytes("first"), OutputFormat.PDF);
        long before = helperPid();

        long start = System.nanoTime();
        var e = assertThrows(LibreOfficeTimeoutException.class, () -> pool.convert(bytes("SPIN"), OutputFormat.PDF));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertTrue(e.getMessage().contains("maximum duration of 1 s"), e.getMessage());
        assertTrue(millis >= 1000 && millis < 4000, "aborted after " + millis + " ms");
        assertArrayEquals(bytes("next"), pool.convert(bytes("next"), OutputFormat.PDF));
        assertNotEquals(before, helperPid(), "instance was not replaced");
    }

    @Test
    @DisplayName("REQ-0102: crashedInstanceFailsOnlyItsConversionAndIsReplaced")
    void crashedInstanceFailsOnlyItsConversionAndIsReplaced() throws Exception {
        pool("ok");
        pool.convert(bytes("first"), OutputFormat.PDF);
        long before = helperPid();

        var e = assertThrows(IOException.class, () -> pool.convert(bytes("CRASH"), OutputFormat.PDF));

        assertTrue(e.getMessage().contains("terminated unexpectedly"), e.getMessage());
        assertArrayEquals(bytes("after crash"), pool.convert(bytes("after crash"), OutputFormat.PDF));
        assertNotEquals(before, helperPid(), "instance was not replaced");
    }

    @Test
    @DisplayName("REQ-0101: instanceIsReplacedAfterTheConfiguredNumberOfConversions")
    void instanceIsReplacedAfterTheConfiguredNumberOfConversions() throws Exception {
        pool("ok");
        pool.maxConversions = 2;
        pool.convert(bytes("1"), OutputFormat.PDF);
        long first = helperPid();
        pool.convert(bytes("2"), OutputFormat.PDF);   // zweite Konvertierung: Grenze erreicht
        pool.convert(bytes("3"), OutputFormat.PDF);   // laeuft schon in der Ersatzinstanz

        assertNotEquals(first, helperPid(), "instance was not replaced after 2 conversions");
    }

    @Test
    @DisplayName("REQ-0101: instanceIsReplacedWhenItsMemoryExceedsTheLimit")
    void instanceIsReplacedWhenItsMemoryExceedsTheLimit() throws Exception {
        pool("ok");
        pool.maxMemoryMb = 0; // jede laufende Instanz liegt darueber
        pool.convert(bytes("1"), OutputFormat.PDF);
        long first = helperPid();
        pool.convert(bytes("2"), OutputFormat.PDF);

        assertNotEquals(first, helperPid(), "instance was not replaced above the memory limit");
    }

    @Test
    @DisplayName("REQ-0098: poolIsReadyOnlyAfterAllInstancesAcceptConversions")
    void poolIsReadyOnlyAfterAllInstancesAcceptConversions() {
        pool("silent");
        pool.maxWorkers = Optional.of(2);
        pool.startTimeout = Duration.ofMillis(500);
        pool.start();
        assertFalse(pool.ready(), "ready although no instance reported READY");
        pool.stop();

        pool("ok");
        pool.maxWorkers = Optional.of(2);
        pool.start();
        assertEquals(2, pool.instances().size());
        assertTrue(pool.instances().stream().allMatch(LibreOfficeInstance::isAlive));
        assertTrue(pool.ready());
    }
}
