package io.github.flaechsig.blocpress.studio;

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
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class StudioHealthIT {
    private static final Logger LOG = LoggerFactory.getLogger(StudioHealthIT.class);
    private static final String IMAGE = System.getProperty("it.image");

    @Container
    static final GenericContainer<?> app =
            new GenericContainer<>(DockerImageName.parse(IMAGE))
                    .withExposedPorts(8082)
                    .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("studio"))
                    .waitingFor(
                            Wait.forHttp("/q/health/ready")
                                    .forPort(8082)
                                    .forStatusCode(200)
                                    .withStartupTimeout(Duration.ofMinutes(2))
                    );

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
