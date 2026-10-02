package io.github.flaechsig.blocpress.e2e.load;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Ergebnis einer Laststufe (eine Konfiguration, eine Parallelitaet). */
record LevelResult(String config, String workers, String label, int parallel, List<Sample> samples,
                   double wallSeconds, CgroupStats before, CgroupStats after, String note) {

    /** Ein einzelner Render: Dauer und ggf. Fehler (HTTP, Timeout oder Inhalt). */
    record Sample(String scenario, double seconds, String error, boolean contentError) {}

    long ok() {
        return samples.stream().filter(s -> s.error() == null).count();
    }

    long contentErrors() {
        return samples.stream().filter(Sample::contentError).count();
    }

    double throughput() {
        return wallSeconds > 0 ? ok() / wallSeconds : 0;
    }

    double percentile(double p) {
        List<Double> sorted = new ArrayList<>(samples.stream().map(Sample::seconds).sorted().toList());
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    /** Fehlerarten mit Anzahl, z.B. "HTTP 500" → 3. */
    Map<String, Integer> errorCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        samples.stream().filter(s -> s.error() != null)
                .forEach(s -> counts.merge(s.scenario() + ": " + s.error(), 1, Integer::sum));
        return counts;
    }

    static String header() {
        return "| Konfiguration | Worker | Pfad | N | Renders | OK | Fehler | Durchsatz/s | p50 s | p95 s | max s "
                + "| Speicherspitze | CPU-s | gedrosselt (Perioden / s) | Hinweis |\n"
                + "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|";
    }

    String row() {
        String memory = after.available() && after.memoryPeak() >= 0 ? CgroupStats.mib(after.memoryPeak()) + "Mi" : "n/a";
        String cpu = after.available() && before.available() ? fmt(after.cpuSecondsSince(before)) : "n/a";
        String throttled = after.available() && before.available()
                ? after.throttledPeriodsSince(before) + " / " + fmt(after.throttledSecondsSince(before))
                : "n/a";
        return String.format(Locale.ROOT, "| %s | %s | %s | %d | %d | %d | %d | %s | %s | %s | %s | %s | %s | %s | %s |",
                config, workers, label, parallel, samples.size(), ok(), samples.size() - ok(), fmt(throughput()),
                fmt(percentile(50)), fmt(percentile(95)), fmt(percentile(100)), memory, cpu, throttled, note);
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }
}
