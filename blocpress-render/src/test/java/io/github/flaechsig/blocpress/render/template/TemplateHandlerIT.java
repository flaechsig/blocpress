package io.github.flaechsig.blocpress.render.template;

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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class TemplateHandlerIT {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateHandlerIT.class);
    private static final String IMAGE = System.getProperty("it.image");
    private static final String DEV_TOKEN;

    static {
        try (var is = TemplateHandlerIT.class.getResourceAsStream("/dev-token.txt")) {
            DEV_TOKEN = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new RuntimeException("Could not read /dev-token.txt from classpath", e);
        }
    }

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
                                " (did maven-dependency-plugin copy it to target/jacoco/jacocoagent.jar?)"
                );
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Container
    static final GenericContainer<?> app =
            new GenericContainer<>(DockerImageName.parse(IMAGE))
                    .withExposedPorts(8080, JACOCO_PORT)
                    .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("app"))
                    .withCreateContainerCmdModifier(cmd ->
                            cmd.getHostConfig().withBinds(
                                    new com.github.dockerjava.api.model.Bind(
                                            JACOCO_DIR.toString(),
                                            new com.github.dockerjava.api.model.Volume("/jacoco"))))
                    .withCopyFileToContainer(
                            org.testcontainers.utility.MountableFile.forHostPath(JACOCO_AGENT_JAR),
                            "/jacoco/jacocoagent.jar"
                    )
                    .withEnv("JAVA_TOOL_OPTIONS",
                            "-javaagent:/jacoco/jacocoagent.jar=output=tcpserver,address=*,port="
                                    + JACOCO_PORT + ",append=true")
                    .waitingFor(
                            Wait.forHttp("/q/health/ready")
                                    .forPort(8080)
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
    void containerStarts() throws Exception {
        var result = app.execInContainer("sh", "-lc", "echo 'Container is running'");
        assertEquals(0, result.getExitCode());
    }

    private static final String VALID_JSON = """
            {
              "datum": "2026-02-10",
              "kunde": {
                "nachname": "Testnachname",
                "vorname": "Testvorname",
                "adresse": {
                  "strasse": "adessoplatz",
                  "hausnummer": "1",
                  "plz": "44269",
                  "ort": "Dortmund"
                },
                "versicherung": {
                  "name": "Handyversicherung AG",
                  "ort": "Düsseldorf",
                  "plz": "12345",
                  "strasse": "Versicherungsstrasse 1"
                },
                "vertrag": {
                  "ende": "2026-07-01",
                  "nummer": "HV123456789",
                  "sparte": "Handyversicherung"
                }
              }
            }
            """;

    @Test
    void mergeTemplatePdf() throws Exception {
        HttpResponse<byte[]> response = sendMergeRequest("application/pdf", VALID_JSON);

        assertEquals(200, response.statusCode(), () -> "Response: " + new String(response.body()));
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.contains("application/pdf"));
        assertTrue(response.body().length > 0, "Response body is empty");
    }

    @Test
    void mergeTemplateRtf() throws Exception {
        HttpResponse<byte[]> response = sendMergeRequest("application/rtf", VALID_JSON);

        assertEquals(200, response.statusCode(), () -> "Response: " + new String(response.body()));
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.contains("application/rtf"));
        assertTrue(response.body().length > 0, "Response body is empty");
    }

    @Test
    void mergeTemplateOdt() throws Exception {
        HttpResponse<byte[]> response = sendMergeRequest("application/vnd.oasis.opendocument.text", VALID_JSON);

        assertEquals(200, response.statusCode(), () -> "Response: " + new String(response.body()));
        assertTrue(response.body().length > 0, "Response body is empty");
    }

    @Test
    void wrongAcceptHeader() throws Exception {
        HttpResponse<byte[]> response = sendMergeRequest("application/json", VALID_JSON);

        assertEquals(406, response.statusCode());
    }

    @Test
    void invalidJsonData() throws Exception {
        HttpResponse<byte[]> response = sendMergeRequest("application/pdf", "{ invalid json !!!");

        assertTrue(response.statusCode() >= 400, "Expected error status but got " + response.statusCode());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        HttpResponse<byte[]> response = sendMergeRequest("application/pdf", VALID_JSON, null);

        assertEquals(401, response.statusCode());
    }

    private HttpResponse<byte[]> sendMergeRequest(String accept, String jsonData) throws IOException, InterruptedException {
        return sendMergeRequest(accept, jsonData, DEV_TOKEN);
    }

    private HttpResponse<byte[]> sendMergeRequest(String accept, String jsonData, String token) throws IOException, InterruptedException {
        URI baseUri = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(8080));
        byte[] templateContent = getClass().getResourceAsStream("/kuendigung_generated.odt").readAllBytes();
        String boundary = "WebKitFormBoundary-" + LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        var out = new DataOutputStream(baos);

        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"template\"; filename=\"template.odt\"\r\n");
        out.writeBytes("Content-Type: application/vnd.oasis.opendocument.text\r\n");
        out.writeBytes("\r\n");
        out.write(templateContent);
        out.writeBytes("\r\n");

        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"data\"\r\n");
        out.writeBytes("Content-Type: application/json; charset=UTF-8\r\n");
        out.writeBytes("\r\n");
        out.write(jsonData.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n");

        out.writeBytes("--" + boundary + "--\r\n");
        out.flush();

        var builder = HttpRequest.newBuilder(baseUri.resolve("/api/template/generate"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", accept);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        var request = builder
                .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                .build();

        try (var client = HttpClient.newBuilder().build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    // --- /template/render endpoint tests ---

    @Test
    void renderDocumentPdf() throws Exception {
        HttpResponse<byte[]> response = sendRenderRequest("pdf", VALID_JSON, DEV_TOKEN);

        assertEquals(200, response.statusCode(), () -> "Response: " + new String(response.body()));
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.contains("application/pdf"));
        assertTrue(response.body().length > 0, "Response body is empty");
    }

    @Test
    void renderDocumentWithoutAuth401() throws Exception {
        HttpResponse<byte[]> response = sendRenderRequest("pdf", VALID_JSON, null);

        assertEquals(401, response.statusCode());
    }

    private HttpResponse<byte[]> sendRenderRequest(String outputType, String jsonData, String token) throws IOException, InterruptedException {
        URI baseUri = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(8080));
        byte[] templateContent = getClass().getResourceAsStream("/kuendigung_generated.odt").readAllBytes();
        String templateBase64 = Base64.getEncoder().encodeToString(templateContent);

        String body = """
                {
                  "template": "%s",
                  "data": %s,
                  "outputType": "%s"
                }
                """.formatted(templateBase64, jsonData, outputType);

        var builder = HttpRequest.newBuilder(baseUri.resolve("/api/template/render"))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        var request = builder
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try (var client = HttpClient.newBuilder().build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }
    }

}