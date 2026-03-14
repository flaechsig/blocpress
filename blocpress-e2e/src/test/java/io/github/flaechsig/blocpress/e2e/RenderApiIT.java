package io.github.flaechsig.blocpress.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Render API running inside the Quickstart container.
 * Tests multipart and JSON endpoints against the render service on port 8081.
 *
 * <p>Run with: {@code mvn verify -pl blocpress-e2e -De2e.skip=false}
 */
class RenderApiIT {

    private static final Logger LOG = LoggerFactory.getLogger(RenderApiIT.class);
    private static final String IMAGE = System.getProperty("studio.image",
            "flaechsig/blocpress-studio-quickstart:latest");
    private static final int RENDER_PORT = 8081;

    private static GenericContainer<?> container;
    private static String renderUrl;
    private static String devToken;
    private static byte[] templateBytes;

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
                }
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
            """;

    @BeforeAll
    static void setUp() throws Exception {
        Assumptions.assumeTrue(isDockerAvailable(), "Docker not available — skipping RenderApiIT");

        try {
            container = new GenericContainer<>(DockerImageName.parse(IMAGE))
                    .withExposedPorts(RENDER_PORT)
                    .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("render"))
                    .waitingFor(
                            Wait.forHttp("/q/health/ready")
                                    .forPort(RENDER_PORT)
                                    .forStatusCode(200)
                                    .withStartupTimeout(Duration.ofMinutes(4))
                    );
            container.start();
        } catch (IllegalStateException e) {
            Assumptions.abort("Docker environment not usable — skipping RenderApiIT: " + e.getMessage());
        }

        renderUrl = "http://" + container.getHost() + ":" + container.getMappedPort(RENDER_PORT);

        try (InputStream is = RenderApiIT.class.getClassLoader().getResourceAsStream("dev-token.txt")) {
            devToken = is != null ? new String(is.readAllBytes()).strip() : "";
        }
        try (InputStream is = RenderApiIT.class.getClassLoader().getResourceAsStream("kuendigung.odt")) {
            assertNotNull(is, "kuendigung.odt not found on classpath");
            templateBytes = is.readAllBytes();
        }
    }

    @AfterAll
    static void tearDown() {
        if (container != null) container.stop();
    }

    private static boolean isDockerAvailable() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                Process p = new ProcessBuilder("docker", "info")
                        .redirectErrorStream(true)
                        .start();
                if (p.waitFor() == 0) return true;
            } catch (Exception e) {
                // docker not on PATH or daemon not running
            }
            if (attempt < 5) {
                LOG.info("Docker not ready yet (attempt {}/5), retrying in 3s...", attempt);
                try { Thread.sleep(3000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    @Test
    void multipartUploadGeneratesPdf() throws Exception {
        HttpResponse<byte[]> response = sendMultipart("application/pdf");
        assertEquals(200, response.statusCode(), () -> "Body: " + new String(response.body()));
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/pdf"));
        assertTrue(response.body().length > 0);
    }

    @Test
    void multipartUploadGeneratesRtf() throws Exception {
        HttpResponse<byte[]> response = sendMultipart("application/rtf");
        assertEquals(200, response.statusCode(), () -> "Body: " + new String(response.body()));
        assertTrue(response.body().length > 0);
    }

    @Test
    void multipartUploadGeneratesOdt() throws Exception {
        HttpResponse<byte[]> response = sendMultipart("application/vnd.oasis.opendocument.text");
        assertEquals(200, response.statusCode(), () -> "Body: " + new String(response.body()));
        assertTrue(response.body().length > 0);
    }

    @Test
    void wrongAcceptHeaderReturns406() throws Exception {
        HttpResponse<byte[]> response = sendMultipart("application/json");
        assertEquals(406, response.statusCode());
    }

    @Test
    void invalidJsonReturnsError() throws Exception {
        HttpResponse<byte[]> response = sendMultipart("application/pdf", "{ invalid json !!!");
        assertTrue(response.statusCode() >= 400,
                "Expected error status but got " + response.statusCode());
    }

    @Test
    void jsonEndpointGeneratesPdf() throws Exception {
        String templateBase64 = Base64.getEncoder().encodeToString(templateBytes);
        String body = """
                {"template": "%s", "data": %s, "outputType": "pdf"}
                """.formatted(templateBase64, VALID_JSON);

        HttpRequest request = HttpRequest.newBuilder(URI.create(renderUrl + "/api/render/template"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + devToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(60))
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode(), () -> "Body: " + new String(response.body()));
            assertTrue(response.body().length > 0);
        }
    }

    // -----------------------------------------------------------------------

    private HttpResponse<byte[]> sendMultipart(String accept) throws Exception {
        return sendMultipart(accept, VALID_JSON);
    }

    private HttpResponse<byte[]> sendMultipart(String accept, String jsonData) throws Exception {
        String boundary = "boundary-" + LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        var out = new DataOutputStream(baos);

        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"template\"; filename=\"template.odt\"\r\n");
        out.writeBytes("Content-Type: application/vnd.oasis.opendocument.text\r\n\r\n");
        out.write(templateBytes);
        out.writeBytes("\r\n");

        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"data\"\r\n");
        out.writeBytes("Content-Type: application/json; charset=UTF-8\r\n\r\n");
        out.write(jsonData.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n");

        out.writeBytes("--" + boundary + "--\r\n");
        out.flush();

        HttpRequest request = HttpRequest.newBuilder(URI.create(renderUrl + "/api/render/template"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", accept)
                .header("Authorization", "Bearer " + devToken)
                .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                .timeout(Duration.ofSeconds(60))
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }
    }
}
