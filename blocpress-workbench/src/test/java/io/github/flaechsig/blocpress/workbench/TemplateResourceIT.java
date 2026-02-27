package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for TemplateResource REST endpoints.
 * Tests actual REST API calls against a running Docker container.
 * Ordered to ensure templates exist for subsequent operations.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemplateResourceIT {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateResourceIT.class);
    private static final String IMAGE = System.getProperty("it.image");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String uploadedTemplateId;

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
                    .withExposedPorts(8081)
                    .withNetwork(network)
                    .withEnv("QUARKUS_DATASOURCE_JDBC_URL", "jdbc:postgresql://postgres:5432/workbench")
                    .withEnv("QUARKUS_DATASOURCE_USERNAME", "workbench")
                    .withEnv("QUARKUS_DATASOURCE_PASSWORD", "workbench")
                    .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("workbench"))
                    .dependsOn(postgres)
                    .waitingFor(
                            Wait.forHttp("/q/health/ready")
                                    .forPort(8081)
                                    .forStatusCode(200)
                                    .withStartupTimeout(Duration.ofMinutes(2))
                    );

    @Test
    @Order(1)
    void testUploadTemplate() throws Exception {
        byte[] odtContent = "fake-odt-content".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = uploadMultipart("test-template", odtContent);

        assertEquals(201, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body());
        assertNotNull(body.get("id"));
        assertEquals("test-template", body.get("name").asText());
        assertTrue(body.has("isValid"));
        uploadedTemplateId = body.get("id").asText();
    }

    @Test
    @Order(2)
    void testListTemplatesAfterUpload() throws Exception {
        HttpResponse<String> response = get("/api/workbench/templates");

        assertEquals(200, response.statusCode());
        JsonNode list = MAPPER.readTree(response.body());
        assertTrue(list.isArray());
        assertEquals(1, list.size());
        assertEquals("test-template", list.get(0).get("name").asText());
    }

    @Test
    @Order(3)
    void testUploadDuplicateNameReturnsConflict() throws Exception {
        byte[] odtContent = "other-content".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = uploadMultipart("test-template", odtContent);

        assertEquals(409, response.statusCode());
    }

    @Test
    @Order(4)
    void testUploadWithoutNameReturnsBadRequest() throws Exception {
        byte[] odtContent = "content".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = uploadMultipart("", odtContent);

        assertEquals(400, response.statusCode());
    }

    @Test
    @Order(5)
    void testDownloadTemplate() throws Exception {
        assertNotNull(uploadedTemplateId);
        HttpResponse<byte[]> response = getBytes("/api/workbench/templates/" + uploadedTemplateId);

        assertEquals(200, response.statusCode());
        String contentDisposition = response.headers().firstValue("Content-Disposition").orElse("");
        assertTrue(contentDisposition.contains("test-template"));
        assertEquals("fake-odt-content", new String(response.body(), StandardCharsets.UTF_8));
    }

    @Test
    @Order(6)
    void testDownloadNonExistentTemplateReturns404() throws Exception {
        HttpResponse<byte[]> response = getBytes("/api/workbench/templates/" + UUID.randomUUID());

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(7)
    void testGetTemplateDetails() throws Exception {
        assertNotNull(uploadedTemplateId);
        HttpResponse<String> response = get("/api/workbench/templates/" + uploadedTemplateId + "/details");

        assertEquals(200, response.statusCode());
        JsonNode details = MAPPER.readTree(response.body());
        assertNotNull(details.get("id"));
        assertEquals("test-template", details.get("name").asText());
        assertNotNull(details.get("status"));
        assertNotNull(details.get("validationResult"));
    }

    @Test
    @Order(8)
    void testGetDetailsOfNonExistentTemplate() throws Exception {
        HttpResponse<String> response = get("/api/workbench/templates/" + UUID.randomUUID() + "/details");

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(9)
    void testSubmitTemplateForApproval() throws Exception {
        assertNotNull(uploadedTemplateId);

        String submitUrl = "/api/workbench/templates/" + uploadedTemplateId + "/submit";
        HttpResponse<String> response = post(submitUrl, "");

        // May succeed or fail depending on validation result
        assertTrue(response.statusCode() == 200 || response.statusCode() == 400);

        if (response.statusCode() == 200) {
            JsonNode body = MAPPER.readTree(response.body());
            assertNotNull(body.get("status"));
        }
    }

    @Test
    @Order(10)
    void testSubmitNonExistentTemplate() throws Exception {
        String submitUrl = "/api/workbench/templates/" + UUID.randomUUID() + "/submit";
        HttpResponse<String> response = post(submitUrl, "");

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(11)
    void testDeleteTemplate() throws Exception {
        // Create a new template to delete
        byte[] odtContent = "template-to-delete".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> uploadResponse = uploadMultipart("delete-me", odtContent);
        assertEquals(201, uploadResponse.statusCode());
        JsonNode uploadBody = MAPPER.readTree(uploadResponse.body());
        String deleteTemplateId = uploadBody.get("id").asText();

        HttpResponse<String> deleteResponse = delete("/api/workbench/templates/" + deleteTemplateId);

        assertEquals(204, deleteResponse.statusCode());
    }

    @Test
    @Order(12)
    void testDeleteNonExistentTemplateReturns404() throws Exception {
        HttpResponse<String> response = delete("/api/workbench/templates/" + UUID.randomUUID());

        assertEquals(404, response.statusCode());
    }

    @Test
    @Order(13)
    void testListTemplatesAfterDelete() throws Exception {
        HttpResponse<String> response = get("/api/workbench/templates");

        assertEquals(200, response.statusCode());
        JsonNode list = MAPPER.readTree(response.body());
        assertTrue(list.isArray());
        // Should still have test-template from Order(1)
        assertTrue(list.size() >= 1);
    }

    @Test
    @Order(14)
    void testUploadMultipleTemplates() throws Exception {
        for (int i = 0; i < 3; i++) {
            byte[] content = ("template-" + i).getBytes();
            HttpResponse<String> response = uploadMultipart("multi-template-" + i, content);
            assertEquals(201, response.statusCode());
        }

        HttpResponse<String> listResponse = get("/api/workbench/templates");
        assertEquals(200, listResponse.statusCode());
        JsonNode list = MAPPER.readTree(listResponse.body());
        assertTrue(list.size() >= 3);
    }

    @Test
    @Order(15)
    void testValidationResultStructure() throws Exception {
        byte[] content = "test-validation".getBytes();
        HttpResponse<String> response = uploadMultipart("validation-structure", content);

        assertEquals(201, response.statusCode());
        JsonNode body = MAPPER.readTree(response.body());

        assertNotNull(body.get("id"));
        assertNotNull(body.get("name"));
        assertTrue(body.has("isValid") || body.has("validationResult"));
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

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(baseUri().resolve(path))
                .POST(HttpRequest.BodyPublishers.ofString(body))
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
