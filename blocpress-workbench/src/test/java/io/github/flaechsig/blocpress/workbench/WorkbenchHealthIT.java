package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkbenchHealthIT {
    private static final Logger LOG = LoggerFactory.getLogger(WorkbenchHealthIT.class);
    private static final String IMAGE = System.getProperty("it.image");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int JACOCO_PORT = 6300;
    private static final Path JACOCO_DIR;
    private static final Path JACOCO_AGENT_JAR;

    private static String uploadedTemplateId;

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

    static final Network network = Network.newNetwork();

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"))
                    .withDatabaseName("workbench")
                    .withUsername("workbench")
                    .withPassword("workbench")
                    .withNetwork(network)
                    .withNetworkAliases("postgres");

    @Container
    static final GenericContainer<?> app =
            new GenericContainer<>(DockerImageName.parse(IMAGE))
                    .withExposedPorts(8081, JACOCO_PORT)
                    .withNetwork(network)
                    .withEnv("QUARKUS_DATASOURCE_JDBC_URL", "jdbc:postgresql://postgres:5432/workbench")
                    .withEnv("QUARKUS_DATASOURCE_USERNAME", "workbench")
                    .withEnv("QUARKUS_DATASOURCE_PASSWORD", "workbench")
                    .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("workbench"))
                    .dependsOn(postgres)
                    .withCopyFileToContainer(
                            org.testcontainers.utility.MountableFile.forHostPath(JACOCO_AGENT_JAR),
                            "/jacoco/jacocoagent.jar")
                    .withEnv("JAVA_TOOL_OPTIONS",
                            "-javaagent:/jacoco/jacocoagent.jar=output=tcpserver,address=*,port="
                                    + JACOCO_PORT + ",append=true")
                    .waitingFor(
                            Wait.forHttp("/q/health/ready")
                                    .forPort(8081)
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
    @Order(1)
    void healthEndpointReturnsUp() throws Exception {
        HttpResponse<String> response = get("/q/health/ready");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("UP"));
    }

    @Test
    @Order(2)
    void workbenchWebComponentIsServed() throws Exception {
        HttpResponse<String> response = get("/components/bp-workbench.js");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("BpWorkbench"));
    }

    @Test
    @Order(3)
    void listTemplatesInitiallyEmpty() throws Exception {
        HttpResponse<String> response = get("/api/workbench/templates");

        assertEquals(200, response.statusCode());
        JsonNode list = MAPPER.readTree(response.body());
        assertTrue(list.isArray());
        assertEquals(0, list.size());
    }

    @Test
    @Order(4)
    void uploadTemplate() throws Exception {
        byte[] odtContent = "fake-odt-content".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = uploadMultipart("test-template", odtContent);

        assertEquals(201, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body());
        assertNotNull(body.get("id"));
        assertEquals("test-template", body.get("name").asText());
        uploadedTemplateId = body.get("id").asText();
    }

    @Test
    @Order(5)
    void uploadDuplicateNameReturnsConflict() throws Exception {
        byte[] odtContent = "other-content".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = uploadMultipart("test-template", odtContent);

        assertEquals(409, response.statusCode());
    }

    @Test
    @Order(6)
    void uploadWithoutNameReturnsBadRequest() throws Exception {
        byte[] odtContent = "content".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = uploadMultipart("", odtContent);

        assertEquals(400, response.statusCode());
    }

    @Test
    @Order(7)
    void listTemplatesAfterUpload() throws Exception {
        HttpResponse<String> response = get("/api/workbench/templates");

        assertEquals(200, response.statusCode());
        JsonNode list = MAPPER.readTree(response.body());
        assertTrue(list.isArray());
        assertEquals(1, list.size());
        assertEquals("test-template", list.get(0).get("name").asText());
    }

    @Test
    @Order(8)
    void downloadTemplate() throws Exception {
        assertNotNull(uploadedTemplateId, "Upload must have succeeded first");
        HttpResponse<byte[]> response = getBytes("/api/workbench/templates/" + uploadedTemplateId);

        assertEquals(200, response.statusCode());
        String contentDisposition = response.headers().firstValue("Content-Disposition").orElse("");
        assertTrue(contentDisposition.contains("test-template"));
        assertEquals("fake-odt-content", new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    @Order(9)
    void downloadNonExistentTemplateReturns404() throws Exception {
        HttpResponse<byte[]> response = getBytes("/api/workbench/templates/" + UUID.randomUUID());

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(10)
    void deleteTemplate() throws Exception {
        assertNotNull(uploadedTemplateId, "Upload must have succeeded first");
        HttpResponse<String> response = delete("/api/workbench/templates/" + uploadedTemplateId);

        assertEquals(204, response.statusCode());
    }

    @Test
    @Order(11)
    void deleteNonExistentTemplateReturns404() throws Exception {
        HttpResponse<String> response = delete("/api/workbench/templates/" + UUID.randomUUID());

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(12)
    void listTemplatesAfterDelete() throws Exception {
        HttpResponse<String> response = get("/api/workbench/templates");

        assertEquals(200, response.statusCode());
        JsonNode list = MAPPER.readTree(response.body());
        assertTrue(list.isArray());
        assertEquals(0, list.size());
    }

    private URI baseUri() {
        return URI.create("http://" + app.getHost() + ":" + app.getMappedPort(8081));
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(baseUri().resolve(path))
                .GET()
                .build();
        try (HttpClient client = HttpClient.newBuilder().build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpResponse<byte[]> getBytes(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(baseUri().resolve(path))
                .GET()
                .build();
        try (HttpClient client = HttpClient.newBuilder().build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private HttpResponse<String> delete(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(baseUri().resolve(path))
                .DELETE()
                .build();
        try (HttpClient client = HttpClient.newBuilder().build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpResponse<String> uploadMultipart(String name, byte[] fileContent) throws Exception {
        String boundary = "TestBoundary-" + System.nanoTime();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"name\"\r\n\r\n");
        out.writeBytes(name);
        out.writeBytes("\r\n");

        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"template.odt\"\r\n");
        out.writeBytes("Content-Type: application/vnd.oasis.opendocument.text\r\n\r\n");
        out.write(fileContent);
        out.writeBytes("\r\n");

        out.writeBytes("--" + boundary + "--\r\n");
        out.flush();

        HttpRequest request = HttpRequest.newBuilder(baseUri().resolve("/api/workbench/templates"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                .build();

        try (HttpClient client = HttpClient.newBuilder().build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
