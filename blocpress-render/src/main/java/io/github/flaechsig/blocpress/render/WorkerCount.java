package io.github.flaechsig.blocpress.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Bestimmt die Zahl gleichzeitiger LibreOffice-Konvertierungen aus dem CPU-Kontingent.
 *
 * <p>Mehr Worker als Kerne senken den Durchsatz (Lastmessung 2026-10-02, US-0034). Die Zahl ist
 * deshalb das CPU-Kontingent des Containers ({@code cpu.max} der cgroup v2), abgerundet und
 * mindestens 1; ohne Limit die Zahl der Prozessoren. Eine eingestellte Zahl
 * ({@code BLOCPRESS_LO_WORKERS}) ist eine Obergrenze: Sie kann die Worker senken, etwa bei
 * knappem Speicher, aber nicht über die Kerne heben.</p>
 *
 * <p>Jeder Worker haelt eine LibreOffice-Instanz dauerhaft im Speicher (ADR-0021). Die Zahl ist
 * deshalb zusaetzlich durch das Speicherlimit ({@code memory.max}) begrenzt: Grundbedarf plus
 * Bedarf je Instanz muessen hineinpassen, mindestens 1 (REQ-0103).</p>
 */
final class WorkerCount {

    static final Path CPU_MAX = Path.of("/sys/fs/cgroup/cpu.max");
    static final Path MEMORY_MAX = Path.of("/sys/fs/cgroup/memory.max");

    private WorkerCount() {
    }

    /** Liest {@code cpu.max} und die Prozessorzahl der JVM. */
    static int resolve(Optional<Integer> configured) {
        return of(configured, read(CPU_MAX), Runtime.getRuntime().availableProcessors());
    }

    /** Wie {@link #resolve(Optional)}, zusaetzlich begrenzt durch {@code memory.max} (REQ-0103). */
    static int resolve(Optional<Integer> configured, long baseBytes, long perInstanceBytes) {
        int byCpu = resolve(configured);
        return Math.min(byCpu, byMemory(read(MEMORY_MAX), baseBytes, perInstanceBytes).orElse(byCpu));
    }

    /**
     * Worker, die das Speicherlimit traegt: (Limit - Grundbedarf) / Bedarf je Instanz, abgerundet,
     * mindestens 1; leer ohne Limit.
     *
     * @param memoryMax Inhalt von {@code memory.max} (Bytes oder „max“), {@code null} wenn nicht vorhanden
     */
    static Optional<Integer> byMemory(String memoryMax, long baseBytes, long perInstanceBytes) {
        if (memoryMax == null || perInstanceBytes <= 0) {
            return Optional.empty();
        }
        try {
            long limit = Long.parseLong(memoryMax.trim());
            long fit = (limit - baseBytes) / perInstanceBytes;
            return Optional.of((int) Math.max(1, Math.min(Integer.MAX_VALUE, fit)));
        } catch (NumberFormatException e) {
            return Optional.empty(); // "max": kein Limit
        }
    }

    private static String read(Path file) {
        try {
            if (Files.isReadable(file)) {
                return Files.readString(file);
            }
        } catch (IOException e) {
            // ohne lesbare Datei gilt kein Limit
        }
        return null;
    }

    /**
     * @param configured eingestellte Obergrenze, leer wenn nicht gesetzt
     * @param cpuMax     Inhalt von {@code cpu.max} („Kontingent Periode“ oder „max Periode“), {@code null} wenn nicht vorhanden
     * @param processors Zahl der Prozessoren
     */
    static int of(Optional<Integer> configured, String cpuMax, int processors) {
        int cores = Math.max(1, Math.min(processors, quota(cpuMax).orElse(processors)));
        return configured.filter(c -> c > 0).map(c -> Math.min(c, cores)).orElse(cores);
    }

    /** Ganze Kerne aus {@code cpu.max}, abgerundet und mindestens 1; leer ohne Limit. */
    private static Optional<Integer> quota(String cpuMax) {
        if (cpuMax == null) {
            return Optional.empty();
        }
        String[] parts = cpuMax.trim().split("\\s+");
        if (parts.length != 2 || parts[0].equals("max")) {
            return Optional.empty();
        }
        try {
            long quota = Long.parseLong(parts[0]);
            long period = Long.parseLong(parts[1]);
            if (quota <= 0 || period <= 0) {
                return Optional.empty();
            }
            return Optional.of((int) Math.max(1, quota / period));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
