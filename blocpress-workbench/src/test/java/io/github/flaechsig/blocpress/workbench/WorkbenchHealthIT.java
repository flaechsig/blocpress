package io.github.flaechsig.blocpress.workbench;

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
class WorkbenchHealthIT {
    private static final Logger LOG = LoggerFactory.getLogger(WorkbenchHealthIT.class);
    private static final String IMAGE = System.getProperty("it.image");

    @Container
    static final GenericContainer<?> app =
            new GenericContainer<>(DockerImageName.parse(IMAGE))
                    .withExposedPorts(8081)
                    .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("workbench"))
                    .waitingFor(
                            Wait.forHttp("/q/health/ready")
                                    .forPort(8081)
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
    void workbenchComponentIsServed() throws Exception {
        HttpResponse<String> response = get("/components/bp-workbench.js");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("BpWorkbench"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        URI base = URI.create("http://" + app.getHost() + ":" + app.getMappedPort(8081));
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .GET()
                .build();
        try (HttpClient client = HttpClient.newBuilder().build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
