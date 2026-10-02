package io.github.flaechsig.blocpress.e2e.load;

import java.util.HashMap;
import java.util.Map;

/**
 * Momentaufnahme der cgroup-v2-Werte eines render-Containers/Pods.
 *
 * <ul>
 *   <li>{@code memory.peak} — Speicherspitze seit Start (daher Neustart vor jeder Stufe)</li>
 *   <li>{@code cpu.stat} — {@code usage_usec}, {@code nr_periods}, {@code nr_throttled},
 *       {@code throttled_usec}; ausgewertet wird die Differenz vorher/nachher</li>
 *   <li>{@code cpu.max} / {@code memory.max} — die gesetzten Limits</li>
 * </ul>
 *
 * <p>{@code throttled_usec} ist ueber alle CPUs aufsummiert und kann die Laufzeit
 * uebersteigen — ein Vergleichswert, keine Wartezeit.</p>
 */
record CgroupStats(Map<String, Long> cpu, long memoryPeak, String cpuMax, String memoryMax) {

    static final CgroupStats UNAVAILABLE = new CgroupStats(Map.of(), -1, "?", "?");

    static CgroupStats read(CgroupReader reader) {
        try {
            Map<String, Long> cpu = new HashMap<>();
            for (String line : reader.read("/sys/fs/cgroup/cpu.stat").split("\n")) {
                String[] kv = line.trim().split("\\s+");
                if (kv.length == 2) {
                    cpu.put(kv[0], Long.parseLong(kv[1]));
                }
            }
            long peak = parseLong(reader.read("/sys/fs/cgroup/memory.peak"));
            return new CgroupStats(cpu, peak,
                    reader.read("/sys/fs/cgroup/cpu.max").trim(),
                    reader.read("/sys/fs/cgroup/memory.max").trim());
        } catch (Exception e) {
            return UNAVAILABLE;
        }
    }

    boolean available() {
        return !cpu.isEmpty();
    }

    /** CPU-Zeit zwischen zwei Aufnahmen in Sekunden. */
    double cpuSecondsSince(CgroupStats before) {
        return delta(before, "usage_usec") / 1e6;
    }

    double throttledSecondsSince(CgroupStats before) {
        return delta(before, "throttled_usec") / 1e6;
    }

    long throttledPeriodsSince(CgroupStats before) {
        return delta(before, "nr_throttled");
    }

    /** {@code cpu.max} lesbar, z.B. "50000 100000" → "0.5", "max 100000" → "unbegrenzt". */
    String cpuLimit() {
        String[] parts = cpuMax.split("\\s+");
        if (parts.length != 2 || parts[0].equals("max")) {
            return parts[0].equals("max") ? "unbegrenzt" : cpuMax;
        }
        return String.format(java.util.Locale.ROOT, "%.2f", Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]));
    }

    String memoryLimit() {
        return memoryMax.equals("max") ? "unbegrenzt" : mib(parseLong(memoryMax)) + "Mi";
    }

    static long mib(long bytes) {
        return Math.round(bytes / 1024.0 / 1024.0);
    }

    private long delta(CgroupStats before, String key) {
        return cpu.getOrDefault(key, 0L) - before.cpu.getOrDefault(key, 0L);
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Liest eine Datei im Container/Pod (docker exec, kubectl exec, Testcontainers). */
    @FunctionalInterface
    interface CgroupReader {
        String read(String path) throws Exception;
    }
}
