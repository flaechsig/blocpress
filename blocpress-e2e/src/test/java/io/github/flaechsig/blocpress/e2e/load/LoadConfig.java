package io.github.flaechsig.blocpress.e2e.load;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Parameter des Lasttests, alle per {@code -Dload.*} ueberschreibbar.
 *
 * <pre>
 *   load.mode       docker (Default) | external | k8s
 *   load.levels     Parallelitaetsstufen, z.B. 1,2,4,8,16
 *   load.requests   Renders je Stufe (Default: max(20, 4 x N))
 *   load.timeout    Client-Timeout je Render in Sekunden (Default 120)
 *   load.async      true = zusaetzlich den Job-Pfad (/api/render/jobs) messen
 *   load.report     Ausgabedatei (Default target/load-report.md)
 *
 *   docker:   load.image, load.cpus (Liste), load.memory (z.B. 1g), load.workers (Liste)
 *   external: load.url, load.docker.container (fuer cgroup + Neustart; optional)
 *   k8s:      load.k8s.namespace, load.k8s.deployment, load.k8s.selector (z.B. app=blocpress-render)
 * </pre>
 */
record LoadConfig(
        String mode,
        List<Integer> levels,
        int requests,
        Duration timeout,
        boolean async,
        String report,
        String image,
        List<Double> cpus,
        String memory,
        List<Integer> workers,
        String url,
        String dockerContainer,
        String k8sNamespace,
        String k8sDeployment,
        String k8sSelector,
        boolean restart
) {

    static LoadConfig fromSystemProperties() {
        return new LoadConfig(
                prop("load.mode", "docker"),
                ints(prop("load.levels", "1,2,4,8,16")),
                Integer.parseInt(prop("load.requests", "0")),
                Duration.ofSeconds(Long.parseLong(prop("load.timeout", "120"))),
                Boolean.parseBoolean(prop("load.async", "false")),
                prop("load.report", "target/load-report.md"),
                prop("load.image", "flaechsig/blocpress-render:latest"),
                Arrays.stream(prop("load.cpus", "0.5,1,2").split(",")).map(String::trim).map(Double::parseDouble).toList(),
                prop("load.memory", "1g"),
                ints(prop("load.workers", "2")),
                prop("load.url", "http://localhost:8080"),
                prop("load.docker.container", ""),
                prop("load.k8s.namespace", "default"),
                prop("load.k8s.deployment", "blocpress-render"),
                prop("load.k8s.selector", "app=blocpress-render"),
                Boolean.parseBoolean(prop("load.restart", "true")));
    }

    /** Renders je Stufe: so viele, dass auch bei hoher Parallelitaet mehrere Runden laufen. */
    int requestsFor(int parallel) {
        return requests > 0 ? requests : Math.max(20, 4 * parallel);
    }

    long memoryBytes() {
        String m = memory.trim().toLowerCase();
        long factor = m.endsWith("g") ? 1L << 30 : m.endsWith("m") ? 1L << 20 : 1L;
        return Long.parseLong(m.replaceAll("[^0-9]", "")) * factor;
    }

    private static String prop(String key, String fallback) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static List<Integer> ints(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).map(Integer::parseInt).toList();
    }
}
