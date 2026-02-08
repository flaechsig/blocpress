package io.github.flaechsig.blocpress.server;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.rtf.RTFEditorKit;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ContainerSmokeIT {
    private static final Logger LOG = LoggerFactory.getLogger(ContainerSmokeIT.class);
    private static String IMAGE = System.getProperty("it.image");

    @Container
    static final GenericContainer<?> app =
            new GenericContainer<>(DockerImageName.parse(IMAGE))
                    .withExposedPorts(8080)
                    .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("app"))
                    .waitingFor(
                            Wait.forHttp("/q/health/ready")
                                    .forStatusCode(200)
                                    .withStartupTimeout(Duration.ofMinutes(2))
                    );

    @Test
    void containerStarts_andLibreOfficeIsInstalled() throws Exception {
        var result = app.execInContainer("sh", "-lc", "soffice --version");
        String out = (result.getStdout() + "\n" + result.getStderr()).trim();

        assertTrue(out.contains("LibreOffice 24.2.7"),
                () -> "Expected LibreOffice 24.2.7 but got:\n" + out);
    }


    @Test
    void renderPdf() throws Exception {
        // Ziel-URL im Host (Testcontainers mappt 8080 -> random host port)
        URI baseUri = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(8080));


        byte[] odt = getClass().getResourceAsStream("/kuendigung_generated.odt").readAllBytes();
        byte[] expected = getClass().getResourceAsStream("/kuendigung_generated.pdf").readAllBytes();

        var request = HttpRequest.newBuilder(
                        baseUri.resolve("/render"))
                .header("Content-Type", "application/vnd.oasis.opendocument.text")
                .header("Accept", "application/pdf")
                .POST(HttpRequest.BodyPublishers.ofByteArray(odt))
                .build();


        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] actual = response.body();

        var tmp = Files.createTempFile("blocpress_out", ".pdf");
        Files.write(tmp, actual);

        assertEquals(200, response.statusCode(), () -> "Response: " + new String(response.body()));
        assertTrue(actual.length > 0, "Response body is empty");
        assertEquals(normalizeText(extractPdfText(expected)), normalizeText(extractPdfText(actual)));
    }

    @Test
    void renderRtf() throws Exception {
        // Ziel-URL im Host (Testcontainers mappt 8080 -> random host port)
        URI baseUri = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(8080));


        byte[] odt = getClass().getResourceAsStream("/kuendigung_generated.odt").readAllBytes();
        byte[] expected = getClass().getResourceAsStream("/kuendigung_generated.rtf").readAllBytes();

        var request = HttpRequest.newBuilder(
                        baseUri.resolve("/render"))
                .header("Content-Type", "application/vnd.oasis.opendocument.text")
                .header("Accept", "application/rtf")
                .POST(HttpRequest.BodyPublishers.ofByteArray(odt))
                .build();


        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] actual = response.body();

        var tmp = Files.createTempFile("blocpress_out", ".rtf");
        Files.write(tmp, actual);

        assertEquals(200, response.statusCode(), () -> "Response: " + new String(response.body()));
        assertTrue(actual.length > 0, "Response body is empty");
        assertEquals(normalizeText(extractRtfText(expected)), normalizeText(extractRtfText(actual)));
    }

    @Test
    void renderOdt() throws Exception {
        // Ziel-URL im Host (Testcontainers mappt 8080 -> random host port)
        URI baseUri = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(8080));


        byte[] odt = getClass().getResourceAsStream("/kuendigung_generated.odt").readAllBytes();
        byte[] expected = getClass().getResourceAsStream("/kuendigung_generated.odt").readAllBytes();

        var request = HttpRequest.newBuilder(
                        baseUri.resolve("/render"))
                .header("Content-Type", "application/vnd.oasis.opendocument.text")
                .header("Accept", "application/vnd.oasis.opendocument.text")
                .POST(HttpRequest.BodyPublishers.ofByteArray(odt))
                .build();


        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] actual = response.body();

        var tmp = Files.createTempFile("blocpress_out", ".odt");
        Files.write(tmp, actual);

        assertEquals(200, response.statusCode(), () -> "Response: " + new String(response.body()));
        assertTrue(actual.length > 0, "Response body is empty");

        String expectedContentXml = normalizeXml(readZipEntry(expected, "content.xml"));
        String actualContentXml = normalizeXml(readZipEntry(actual, "content.xml"));
        assertEquals(normalizeText(expectedContentXml), normalizeText(actualContentXml));
    }


    private static String extractPdfText(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            var stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private static String extractRtfText(byte[] rtf) throws Exception {
        var kit = new RTFEditorKit();
        var doc = new DefaultStyledDocument();
        try (InputStream in = new ByteArrayInputStream(rtf)) {
            kit.read(in, doc, 0);
        }
        return doc.getText(0, doc.getLength());
    }

    private static String readZipEntry(byte[] zipBytes, String entryName) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (entryName.equals(e.getName())) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        fail("ZIP entry not found: " + entryName);
        return null; // unreachable
    }

    private static String normalizeText(String s) {
        // Vereinheitlicht Zeilenenden/Whitespace, damit Layout-Minidiffs nicht flaken
        return s.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static String normalizeXml(String xml) {
        // Minimal-Normalisierung (keine Canonicalization), meist ausreichend für content.xml
        return xml.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll(">\\s+<", "><")
                .trim();
    }
}