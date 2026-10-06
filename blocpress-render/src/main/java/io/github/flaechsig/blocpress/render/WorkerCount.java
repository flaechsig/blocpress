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
 */
final class WorkerCount {

    static final Path CPU_MAX = Path.of("/sys/fs/cgroup/cpu.max");

    private WorkerCount() {
    }

    /** Liest {@code cpu.max} und die Prozessorzahl der JVM. */
    static int resolve(Optional<Integer> configured) {
        String cpuMax = null;
        try {
            if (Files.isReadable(CPU_MAX)) {
                cpuMax = Files.readString(CPU_MAX);
            }
        } catch (IOException e) {
            // ohne lesbares Kontingent zaehlt die Prozessorzahl
        }
        return of(configured, cpuMax, Runtime.getRuntime().availableProcessors());
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
