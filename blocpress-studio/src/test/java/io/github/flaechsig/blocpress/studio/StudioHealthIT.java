package io.github.flaechsig.blocpress.studio;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class StudioHealthIT {
    private static final Logger LOG = LoggerFactory.getLogger(StudioHealthIT.class);
    private static final String IMAGE = System.getProperty("it.image");

    private static final int JACOCO_PORT = 6300;
    private static final Path JACOCO_DIR;
    private static final Path JACOCO_AGENT_JAR;

    static {
        try {
            JACOCO_DIR = Path.of("target", "jacoco-it").toAbsolutePath();
            Files.createDirectories(JACOCO_DIR);

            JACOCO_AGENT_JAR = Path.of("target", "jacoco", "jacocoagent.jar").toAbsolutePath();
            if (!Files.exists(JACOCO_AGENT_JAR)) {
                throw new IllegalStateException(
                        "JaCoCo agent jar not found: " + JACOCO_AGENT_JAR +
                                " (did maven-dependency-plugin copy it to target/jacoco/jacocoagent.jar?)");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Container
    static final GenericContainer<?> app =
            new GenericContainer<>(DockerImageName.parse(IMAGE))
                    .withExposedPorts(8082, JACOCO_PORT)
                    .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("studio"))
                    .withCopyFileToContainer(
                            org.testcontainers.utility.MountableFile.forHostPath(JACOCO_AGENT_JAR),
                            "/jacoco/jacocoagent.jar")
                    .withEnv("JAVA_TOOL_OPTIONS",
                            "-javaagent:/jacoco/jacocoagent.jar=output=tcpserver,address=*,port="
                                    + JACOCO_PORT + ",append=true")
                    .waitingFor(
                            Wait.forHttp("/q/health/ready")
                                    .forPort(8082)
                                    .forStatusCode(200)
                                    .withStartupTimeout(Duration.ofMinutes(2))
                    );

    @AfterAll
    static void dumpJacocoExecFromContainer() throws Exception {
        Path destExec = JACOCO_DIR.resolve("jacoco-it.exec");

        String host = app.getHost();
        int port = app.getMappedPort(JACOCO_PORT);

        org.jacoco.core.tools.ExecFileLoader loader = new org.jacoco.core.tools.ExecDumpClient().dump(host, port);
        loader.save(destExec.toFile(), true);

        long bytes = Files.exists(destExec) ? Files.size(destExec) : 0;
        LOG.info("JaCoCo exec dumped to {} ({} bytes)", destExec, bytes);
        if (bytes == 0) {
            throw new IllegalStateException("Dump produced empty exec file: " + destExec);
        }
    }

    @Test
    void healthEndpointReturnsUp() throws Exception {
        HttpResponse<String> response = get("/q/health/ready");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("UP"));
    }

    @Test
    void indexHtmlIsServed() throws Exception {
        HttpResponse<String> response = get("/index.html");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("<bp-app>"));
    }

    @Test
    void jsFilesAreReachable() throws Exception {
        for (String path : new String[]{
                "/components/bp-app.js",
                "/components/bp-nav.js",
                "/components/bp-token-input.js",
                "/components/bp-router.js"
        }) {
            HttpResponse<String> response = get(path);
            assertEquals(200, response.statusCode(), "Expected 200 for " + path);
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        URI base = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(8082));
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .GET()
                .build();
        try (HttpClient client = HttpClient.newBuilder().build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
