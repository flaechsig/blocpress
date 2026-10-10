package io.github.flaechsig.blocpress.render;

import io.github.flaechsig.blocpress.core.OutputFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-0099: Konvertierungen laufen in der laufenden LibreOffice-Instanz; zwischen ihnen entsteht
 * kein neuer Prozess. Mit echtem LibreOffice und dem mitgelieferten Helfer.
 */
class LibreOfficeWarmTest {

    private final LibreOfficePool pool = new LibreOfficePool();

    @AfterEach
    void stop() {
        pool.stop();
    }

    @Test
    @DisplayName("REQ-0099: conversionsRunInTheRunningInstanceWithoutNewProcesses")
    void conversionsRunInTheRunningInstanceWithoutNewProcesses() throws Exception {
        pool.maxWorkers = Optional.of(1);
        byte[] odt = Files.readAllBytes(Path.of("src/test/resources/kuendigung.odt"));

        byte[] first = pool.convert(odt, OutputFormat.PDF);
        Set<Long> processes = processes();
        assertFalse(processes.isEmpty());

        for (OutputFormat format : new OutputFormat[]{OutputFormat.PDF, OutputFormat.RTF, OutputFormat.ODT, OutputFormat.PDF}) {
            byte[] result = pool.convert(odt, format);
            assertTrue(result.length > 0, format + " is empty");
            assertEquals(processes, processes(), "processes changed during a " + format + " conversion");
        }
        assertTrue(new String(first, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF"), "not a PDF");
        assertEquals(5, pool.instances().get(0).conversions());
    }

    /** Helfer und alle Unterprozesse (soffice) der einen Instanz. */
    private Set<Long> processes() {
        long helper = pool.instances().get(0).pid();
        ProcessHandle handle = ProcessHandle.of(helper).orElseThrow();
        Set<Long> pids = handle.descendants().map(ProcessHandle::pid).collect(Collectors.toSet());
        pids.add(helper);
        return pids;
    }
}
