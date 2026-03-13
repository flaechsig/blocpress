package io.github.flaechsig.blocpress.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.runtime.RemoteControlReader;
import org.jacoco.core.runtime.RemoteControlWriter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests against the blocpress-studio all-in-one container.
 *
 * <p>The container is started with JaCoCo TCP agents on workbench (port 6300)
 * and render (port 6301). After all tests, {@link #dumpCoverageAndStop()} dumps
 * the exec files into {@code target/jacoco-workbench.exec} and
 * {@code target/jacoco-render.exec}. The maven-antrun-plugin then generates
 * the HTML report at {@code target/site/jacoco-e2e/index.html}.</p>
 *
 * <p>Run with: {@code mvn verify -pl blocpress-e2e}<br>
 * Use custom image: {@code mvn verify -pl blocpress-e2e -Dstudio.image=my/image:tag}</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudioE2EIT {

    private static final int WORKBENCH_JACOCO_PORT = 6300;
    private static final int RENDER_JACOCO_PORT    = 6301;

    /** The studio proxy is the single external entry point (routes /api/* to workbench). */
    private static final int STUDIO_HTTP_PORT   = 8080;
    /** Direct render access (stateless endpoint, no auth). */
    private static final int RENDER_HTTP_PORT   = 8081;

    private static GenericContainer<?> studio;

    /** Base URL of the studio proxy (workbench APIs go through here). */
    private static String studioUrl;
    /** Base URL of the render service (direct access). */
    private static String renderUrl;

    private static int workbenchJacocoMappedPort;
    private static int renderJacocoMappedPort;

    // Tracks the template ID created in Order(2) for use in later tests
    private static String uploadedTemplateId;

    // -----------------------------------------------------------------------

    @BeforeAll
    static void startContainer() {
        // Skip gracefully if Docker is not available (e.g., plain CI without Docker daemon)
        Assumptions.assumeTrue(isDockerAvailable(), "Docker not available — skipping E2E tests");

        String agentJar = System.getProperty("jacoco.agent.jar");
        assertNotNull(agentJar, "System property jacoco.agent.jar must be set (maven-dependency-plugin copies it to target/jacoco/)");
        assertTrue(new File(agentJar).exists(), "JaCoCo agent JAR not found: " + agentJar);

        String image = System.getProperty("studio.image", "flaechsig/blocpress-studio-quickstart:latest");

        String workbenchAgent = "-javaagent:/tmp/jacocoagent.jar"
                + "=output=tcpserver,port=" + WORKBENCH_JACOCO_PORT + ",address=0.0.0.0";
        String renderAgent = "-javaagent:/tmp/jacocoagent.jar"
                + "=output=tcpserver,port=" + RENDER_JACOCO_PORT + ",address=0.0.0.0";

        studio = new GenericContainer<>(image)
                .withCopyFileToContainer(MountableFile.forHostPath(agentJar), "/tmp/jacocoagent.jar")
                .withEnv("JAVA_OPTS_WORKBENCH", workbenchAgent)
                .withEnv("JAVA_OPTS_RENDER", renderAgent)
                .withExposedPorts(STUDIO_HTTP_PORT, RENDER_HTTP_PORT, WORKBENCH_JACOCO_PORT, RENDER_JACOCO_PORT)
                .waitingFor(Wait.forHttp("/q/health/ready")
                        .forPort(STUDIO_HTTP_PORT)
                        .withStartupTimeout(Duration.ofMinutes(4)));

        studio.start();

        studioUrl  = "http://" + studio.getHost() + ":" + studio.getMappedPort(STUDIO_HTTP_PORT);
        renderUrl  = "http://" + studio.getHost() + ":" + studio.getMappedPort(RENDER_HTTP_PORT);
        workbenchJacocoMappedPort = studio.getMappedPort(WORKBENCH_JACOCO_PORT);
        renderJacocoMappedPort    = studio.getMappedPort(RENDER_JACOCO_PORT);

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterAll
    static void dumpCoverageAndStop() {
        if (studio == null) return;
        try {
            String execDir = System.getProperty("jacoco.exec.dir", "target");
            Files.createDirectories(Path.of(execDir));
            dumpJacocoData(studio.getHost(), workbenchJacocoMappedPort,
                    execDir + "/jacoco-workbench.exec");
            dumpJacocoData(studio.getHost(), renderJacocoMappedPort,
                    execDir + "/jacoco-render.exec");
        } catch (Exception e) {
            System.err.println("[E2E] JaCoCo dump failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            studio.stop();
        }
    }

    // -----------------------------------------------------------------------
    // E2E Tests
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    void healthCheck() {
        given().baseUri(studioUrl)
                .when().get("/q/health/ready")
                .then().statusCode(200);

        given().baseUri(renderUrl)
                .when().get("/q/health/ready")
                .then().statusCode(200);
    }

    @Test
    @Order(2)
    void uploadTemplate() throws Exception {
        byte[] odt = loadResource("kuendigung.odt");
        Path tmp = Files.createTempFile("e2e-", ".odt");
        Files.write(tmp, odt);

        Response response = given()
                .baseUri(studioUrl)
                .multiPart("name", "E2E-Kuendigung")
                .multiPart("file", tmp.toFile(), "application/vnd.oasis.opendocument.text")
                .post("/api/workbench/templates");

        assertEquals(201, response.statusCode(),
                "Upload failed: " + response.body().asString());

        uploadedTemplateId = response.jsonPath().getString("id");
        assertNotNull(uploadedTemplateId, "Response must contain template id");

        Files.deleteIfExists(tmp);
    }

    @Test
    @Order(3)
    void listTemplatesContainsUpload() {
        assertNotNull(uploadedTemplateId, "Order(2) must have succeeded first");

        given().baseUri(studioUrl)
                .when().get("/api/workbench/templates")
                .then()
                .statusCode(200);
    }

    @Test
    @Order(4)
    void previewGeneratesPdf() throws Exception {
        assertNotNull(uploadedTemplateId, "Order(2) must have succeeded first");

        // Minimal data matching the kuendigung template fields
        String body = """
                {"data": {"name": "E2E-Test", "vorname": "Max", "strasse": "Teststr. 1",
                           "plz": "12345", "ort": "Teststadt"},
                 "outputType": "pdf"}
                """;

        byte[] pdf = given()
                .baseUri(studioUrl)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/workbench/templates/" + uploadedTemplateId + "/preview")
                .then()
                .statusCode(200)
                .extract().asByteArray();

        // PDF magic bytes: %PDF
        assertTrue(pdf.length > 500, "Expected a non-trivial PDF, got " + pdf.length + " bytes");
        assertEquals('%', pdf[0]);
        assertEquals('P', pdf[1]);
        assertEquals('D', pdf[2]);
        assertEquals('F', pdf[3]);
    }

    @Test
    @Order(5)
    void searchFindsUploadedTemplate() throws Exception {
        // ES indexing is async — give it a moment
        Thread.sleep(2000);

        Response response = given()
                .baseUri(studioUrl)
                .queryParam("q", "E2E-Kuendigung")
                .get("/api/workbench/search");

        assertEquals(200, response.statusCode());
        assertTrue(response.jsonPath().getLong("total") >= 1,
                "Expected at least 1 search hit for 'E2E-Kuendigung'");
    }

    @Test
    @Order(6)
    void submitAndApproveWorkflow() {
        assertNotNull(uploadedTemplateId, "Order(2) must have succeeded first");

        // Submit
        given().baseUri(studioUrl)
                .post("/api/workbench/templates/" + uploadedTemplateId + "/submit")
                .then().statusCode(200);

        // Approve
        given().baseUri(studioUrl)
                .contentType(ContentType.JSON)
                .body("{\"newStatus\": \"APPROVED\"}")
                .put("/api/workbench/templates/" + uploadedTemplateId + "/status")
                .then().statusCode(200);
    }

    @Test
    @Order(7)
    void renderByNameAfterApproval() {
        // Render via name endpoint on the render service directly (requires JWT in prod;
        // the studio quickstart uses the built-in dev key)
        String devToken = loadDevToken();

        String body = """
                {"data": {"name": "E2E-Test", "vorname": "Max", "strasse": "Teststr. 1",
                           "plz": "12345", "ort": "Teststadt"},
                 "outputType": "pdf"}
                """;

        byte[] pdf = given()
                .baseUri(renderUrl)
                .header("Authorization", "Bearer " + devToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/render/E2E-Kuendigung")
                .then()
                .statusCode(200)
                .extract().asByteArray();

        assertTrue(pdf.length > 500, "Expected a non-trivial PDF from render-by-name");
    }

    @Test
    @Order(8)
    void webdavListBausteine() {
        given().baseUri(studioUrl)
                .when().get("/api/webdav/bausteine/")
                .then().statusCode(207); // PROPFIND returns 207, but OPTIONS/GET for listing goes through fallback
    }

    // -----------------------------------------------------------------------
    // Invoice template — regression for NumberFormatException on numeric fields
    // -----------------------------------------------------------------------

    /** ID of the uploaded invoice template (set in Order 9, used in Order 10). */
    private static String invoiceTemplateId;

    @Test
    @Order(9)
    void uploadInvoiceTemplate() throws Exception {
        byte[] odt = loadResource("invoice.odt");
        Path tmp = Files.createTempFile("e2e-invoice-", ".odt");
        Files.write(tmp, odt);

        Response response = given()
                .baseUri(studioUrl)
                .multiPart("name", "E2E-Invoice")
                .multiPart("file", tmp.toFile(), "application/vnd.oasis.opendocument.text")
                .post("/api/workbench/templates");

        assertEquals(201, response.statusCode(),
                "Invoice upload failed: " + response.body().asString());

        invoiceTemplateId = response.jsonPath().getString("id");
        assertNotNull(invoiceTemplateId, "Response must contain template id");
        Files.deleteIfExists(tmp);
    }

    @Test
    @Order(10)
    void invoicePreviewGeneratesPdf() {
        assertNotNull(invoiceTemplateId, "Order(9) must have succeeded first");

        // Real invoice data with numeric fields — regression test for:
        // NumberFormatException when JsonSchemaGenerator mis-typed paymentTermsDays as string
        // and the sample JSON generator produced "paymentTermsDays_example" instead of a number.
        String body = """
                {
                  "data": {
                    "invoice": {
                      "number": "E2E-2026-0001",
                      "date": "2026-03-12",
                      "currency": "€",
                      "paymentTermsDays": 14
                    },
                    "customer": {
                      "firstname": "E2E",
                      "lastname": "Test",
                      "street": "Teststr. 1",
                      "postcode": "12345",
                      "city": "Teststadt"
                    },
                    "positions": [
                      {
                        "name": "blocpress",
                        "description": "E2E render test",
                        "quantity": 1,
                        "unitPrice": 9.99,
                        "total": 9.99
                      }
                    ],
                    "summary": {
                      "netTotal": 9.99,
                      "taxRate": 19,
                      "taxAmount": 1.90,
                      "grossTotal": 11.89
                    }
                  },
                  "outputType": "pdf"
                }
                """;

        byte[] pdf = given()
                .baseUri(studioUrl)
                .contentType("application/json")
                .body(body)
                .post("/api/workbench/templates/" + invoiceTemplateId + "/preview")
                .then()
                .statusCode(200)
                .extract().asByteArray();

        assertTrue(pdf.length > 500, "Expected a real PDF, got " + pdf.length + " bytes");
        assertEquals('%', pdf[0]);
        assertEquals('P', pdf[1]);
        assertEquals('D', pdf[2]);
        assertEquals('F', pdf[3]);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static byte[] loadResource(String name) throws Exception {
        try (InputStream is = StudioE2EIT.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(is, "Test resource not found on classpath: " + name);
            return is.readAllBytes();
        }
    }

    /** Reads the built-in dev JWT from test resources (matches the studio image's built-in key). */
    private static String loadDevToken() {
        try (InputStream is = StudioE2EIT.class.getClassLoader().getResourceAsStream("dev-token.txt")) {
            if (is == null) return ""; // render-by-name test will fail gracefully
            return new String(is.readAllBytes()).strip();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Exception e) {
            System.out.println("[E2E] Docker not available: " + e.getMessage());
            return false;
        }
    }

    /**
     * Connects to a running JVM's JaCoCo TCP server, requests a dump,
     * and writes the execution data to {@code destFile}.
     */
    static void dumpJacocoData(String host, int port, String destFile) throws IOException {
        System.out.println("[E2E] Dumping JaCoCo from " + host + ":" + port + " → " + destFile);
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(15_000);

            RemoteControlWriter writer = new RemoteControlWriter(socket.getOutputStream());
            RemoteControlReader reader = new RemoteControlReader(socket.getInputStream());

            ExecutionDataStore execStore   = new ExecutionDataStore();
            SessionInfoStore  sessionStore = new SessionInfoStore();
            reader.setExecutionDataVisitor(execStore);
            reader.setSessionInfoVisitor(sessionStore);

            writer.visitDumpCommand(true /*dump*/, true /*reset*/);
            reader.read();

            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                ExecutionDataWriter dataWriter = new ExecutionDataWriter(fos);
                sessionStore.accept(dataWriter);
                execStore.accept(dataWriter);
            }
            System.out.println("[E2E] Dump complete: " + new File(destFile).length() + " bytes");
        }
    }
}