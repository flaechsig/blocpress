package io.github.flaechsig.blocpress.e2e.load;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Die zu messende render-Instanz. {@link #restart()} liefert vor jeder Laststufe eine frische
 * Instanz, damit {@code memory.peak} nur diese Stufe zeigt.
 */
interface RenderTarget extends AutoCloseable {

    /** Kurzbeschreibung der Konfiguration fuer den Report. */
    String describe();

    /** Anzahl LibreOffice-Worker ({@code BLOCPRESS_LO_WORKERS}), soweit bekannt. */
    String workers();

    void restart() throws Exception;

    String baseUrl();

    CgroupStats stats();

    /** Zusatzinfo nach einer Stufe, z.B. "OOMKilled" — sonst leer. */
    default String afterLevelNote() {
        return "";
    }

    @Override
    void close();

    // ---------------------------------------------------------------- Hilfen

    static void awaitReady(String baseUrl, Duration timeout) throws InterruptedException {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                var response = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/q/health/ready"))
                        .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException e) {
                // noch nicht erreichbar
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("render nicht bereit nach " + timeout + ": " + baseUrl);
    }

    static String exec(List<String> command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) {
            throw new IOException(String.join(" ", command) + " -> " + out.trim());
        }
        return out;
    }

    // ---------------------------------------------------------------- docker (verwaltet)

    /**
     * Startet render (+ PostgreSQL) selbst, mit {@code --cpus}/{@code --memory} als Gegenstueck
     * zu Kubernetes-Limits.
     */
    final class Docker implements RenderTarget {
        private final String image;
        private final double cpus;
        private final long memoryBytes;
        private final int workers;
        private final Network network;
        private GenericContainer<?> render;
        private boolean oomKilled;

        Docker(String image, double cpus, long memoryBytes, int workers, Network network) {
            this.image = image;
            this.cpus = cpus;
            this.memoryBytes = memoryBytes;
            this.workers = workers;
            this.network = network;
        }

        @Override
        public String describe() {
            return String.format(Locale.ROOT, "docker --cpus=%s --memory=%dMi, %d Worker",
                    trim(cpus), CgroupStats.mib(memoryBytes), workers);
        }

        @Override
        public String workers() {
            return String.valueOf(workers);
        }

        @Override
        @SuppressWarnings("resource")
        public void restart() {
            close();
            oomKilled = false;
            render = new GenericContainer<>(DockerImageName.parse(image))
                    .withNetwork(network)
                    .withExposedPorts(8080)
                    .withEnv("QUARKUS_DATASOURCE_JDBC_URL", "jdbc:postgresql://loadtest-db:5432/production")
                    .withEnv("QUARKUS_HIBERNATE_ORM_SCHEMA_MANAGEMENT_STRATEGY", "drop-and-create")
                    .withEnv("BLOCPRESS_LO_WORKERS", String.valueOf(workers))
                    .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                            .withNanoCPUs((long) (cpus * 1_000_000_000L))
                            .withMemory(memoryBytes)
                            .withMemorySwap(memoryBytes))
                    .waitingFor(Wait.forHttp("/q/health/ready").forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(3)));
            render.start();
        }

        @Override
        public String baseUrl() {
            return "http://" + render.getHost() + ":" + render.getMappedPort(8080);
        }

        @Override
        public CgroupStats stats() {
            return CgroupStats.read(path -> render.execInContainer("cat", path).getStdout());
        }

        @Override
        public String afterLevelNote() {
            var state = render.getDockerClient().inspectContainerCmd(render.getContainerId()).exec().getState();
            oomKilled = Boolean.TRUE.equals(state.getOOMKilled());
            return oomKilled ? "OOMKilled" : Boolean.TRUE.equals(state.getRunning()) ? "" : "Container beendet";
        }

        @Override
        public void close() {
            if (render != null) {
                render.stop();
                render = null;
            }
        }

        static GenericContainer<?> postgres(Network network) {
            return new GenericContainer<>(DockerImageName.parse("postgres:16"))
                    .withNetwork(network)
                    .withNetworkAliases("loadtest-db")
                    .withEnv("POSTGRES_DB", "production")
                    .withEnv("POSTGRES_USER", "workbench")
                    .withEnv("POSTGRES_PASSWORD", "workbench")
                    .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2));
        }

        private static String trim(double d) {
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
    }

    // ---------------------------------------------------------------- external (z.B. docker-compose)

    /** Laufende Instanz unter {@code load.url}; cgroup + Neustart ueber {@code docker} (optional). */
    final class External implements RenderTarget {
        private final String url;
        private final String container;
        private final boolean restart;

        External(String url, String container, boolean restart) {
            this.url = url;
            this.container = container;
            this.restart = restart;
        }

        @Override
        public String describe() {
            return "external " + url + (container.isEmpty() ? "" : " (Container " + container + ")");
        }

        @Override
        public String workers() {
            if (container.isEmpty()) {
                return "?";
            }
            try {
                String w = exec(List.of("docker", "exec", container, "sh", "-c", "echo ${BLOCPRESS_LO_WORKERS:-2}")).trim();
                return w.isEmpty() ? "?" : w;
            } catch (Exception e) {
                return "?";
            }
        }

        @Override
        public void restart() throws Exception {
            if (restart && !container.isEmpty()) {
                exec(List.of("docker", "restart", container));
            }
            awaitReady(url, Duration.ofMinutes(3));
        }

        @Override
        public String baseUrl() {
            return url;
        }

        @Override
        public CgroupStats stats() {
            if (container.isEmpty()) {
                return CgroupStats.UNAVAILABLE;
            }
            return CgroupStats.read(path -> exec(List.of("docker", "exec", container, "cat", path)));
        }

        @Override
        public void close() {
            // fremde Instanz — nichts zu beenden
        }
    }

    // ---------------------------------------------------------------- Kubernetes

    /**
     * render-Deployment in Kubernetes. Vor jeder Stufe {@code kubectl rollout restart}, danach
     * Port-Forward auf den neuen Pod; cgroup-Werte per {@code kubectl exec}.
     */
    final class Kubernetes implements RenderTarget {
        private final String namespace;
        private final String deployment;
        private final String selector;
        private final boolean restart;
        private String pod;
        private Process portForward;
        private int localPort;

        Kubernetes(String namespace, String deployment, String selector, boolean restart) {
            this.namespace = namespace;
            this.deployment = deployment;
            this.selector = selector;
            this.restart = restart;
        }

        @Override
        public String describe() {
            return "k8s " + namespace + "/" + deployment + (pod == null ? "" : " (Pod " + pod + ")");
        }

        @Override
        public String workers() {
            try {
                String w = kubectl("exec", pod, "--", "sh", "-c", "echo ${BLOCPRESS_LO_WORKERS:-2}").trim();
                return w.isEmpty() ? "?" : w;
            } catch (Exception e) {
                return "?";
            }
        }

        @Override
        public void restart() throws Exception {
            stopPortForward();
            if (restart) {
                kubectl("rollout", "restart", "deployment/" + deployment);
            }
            kubectl("rollout", "status", "deployment/" + deployment, "--timeout=180s");
            pod = currentPod();
            try (ServerSocket socket = new ServerSocket(0)) {
                localPort = socket.getLocalPort();
            }
            portForward = new ProcessBuilder("kubectl", "-n", namespace, "port-forward", "pod/" + pod,
                    localPort + ":8080").redirectErrorStream(true).start();
            awaitReady(baseUrl(), Duration.ofMinutes(3));
        }

        private String currentPod() throws Exception {
            // nach einem Rollout kann der alte Pod noch terminieren — den laufenden, juengsten nehmen
            String pods = kubectl("get", "pods", "-l", selector, "--field-selector=status.phase=Running",
                    "--sort-by=.metadata.creationTimestamp", "-o", "jsonpath={range .items[*]}{.metadata.name}{\"\\n\"}{end}");
            List<String> names = pods.lines().filter(l -> !l.isBlank()).toList();
            if (names.isEmpty()) {
                throw new IllegalStateException("kein laufender Pod fuer " + selector);
            }
            return names.getLast();
        }

        @Override
        public String baseUrl() {
            return "http://localhost:" + localPort;
        }

        @Override
        public CgroupStats stats() {
            return CgroupStats.read(path -> kubectl("exec", pod, "--", "cat", path));
        }

        @Override
        public String afterLevelNote() {
            try {
                String reason = kubectl("get", "pod", pod, "-o",
                        "jsonpath={.status.containerStatuses[0].lastState.terminated.reason}").trim();
                return reason.isEmpty() ? "" : reason;
            } catch (Exception e) {
                return "";
            }
        }

        @Override
        public void close() {
            stopPortForward();
        }

        private void stopPortForward() {
            if (portForward != null) {
                portForward.destroy();
                portForward = null;
            }
        }

        private String kubectl(String... args) throws IOException, InterruptedException {
            var cmd = new java.util.ArrayList<>(List.of("kubectl", "-n", namespace));
            cmd.addAll(List.of(args));
            return exec(cmd);
        }
    }
}
