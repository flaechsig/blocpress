package io.github.flaechsig.blocpress.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-0027: Die Worker-Zahl folgt dem CPU-Kontingent; die Einstellung ist nur eine Obergrenze. */
class WorkerCountTest {

    @ParameterizedTest(name = "{displayName} [{index}] cpu.max=''{0}'', Prozessoren={1} → {2}")
    @DisplayName("REQ-0027: workersFollowCpuQuotaRoundedDown")
    @CsvSource(delimiter = '|', value = {
            "50000 100000|8|1",     // 0,5 CPU: mindestens 1
            "100000 100000|8|1",
            "150000 100000|8|1",    // 1,5 CPU: abgerundet
            "200000 100000|8|2",    // Standardgröße 2 CPU
            "400000 100000|8|4",
            "800000 100000|4|4",    // Kontingent über den Prozessoren: höchstens die Prozessoren
            "max 100000|6|6",       // kein Limit
            "unlesbar|3|3"
    })
    void workersFollowCpuQuotaRoundedDown(String cpuMax, int processors, int expected) {
        assertEquals(expected, WorkerCount.of(Optional.empty(), cpuMax, processors));
    }

    @Test
    @DisplayName("REQ-0027: withoutCgroupTheProcessorsCount")
    void withoutCgroupTheProcessorsCount() {
        assertEquals(3, WorkerCount.of(Optional.empty(), null, 3));
    }

    @ParameterizedTest(name = "{displayName} [{index}] Grenze={0}, cpu.max=''{1}'' → {2}")
    @DisplayName("REQ-0027: configuredWorkersAreAnUpperLimit")
    @CsvSource(delimiter = '|', value = {
            "1|400000 100000|1",    // senkt die Zahl, etwa bei knappem Speicher
            "2|400000 100000|2",
            "4|200000 100000|2",    // hebt sie nicht über die Kerne
            "8|50000 100000|1",
            "0|200000 100000|2"     // kleiner 1 zählt als nicht gesetzt
    })
    void configuredWorkersAreAnUpperLimit(int configured, String cpuMax, int expected) {
        assertEquals(expected, WorkerCount.of(Optional.of(configured), cpuMax, 8));
    }

    @Test
    @DisplayName("REQ-0027: poolUsesAtLeastOneWorkerOnThisMachine")
    void poolUsesAtLeastOneWorkerOnThisMachine() {
        int workers = new LibreOfficePool().workers();
        assertTrue(workers >= 1 && workers <= Runtime.getRuntime().availableProcessors(), "workers=" + workers);
    }
}
