package io.github.flaechsig.blocpress.render;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Sendet fire-and-forget HTTP-Callbacks wenn ein Render-Job abgeschlossen ist.
 */
@ApplicationScoped
public class WebhookSender {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookSender.class);

    public void sendAsync(String webhookUrl, UUID jobId, RenderJobStatus status) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }
        Thread.ofVirtual().start(() -> send(webhookUrl, jobId, status));
    }

    private void send(String webhookUrl, UUID jobId, RenderJobStatus status) {
        String body = String.format("{\"jobId\":\"%s\",\"status\":\"%s\"}", jobId, status);
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            LOG.info("Webhook sent to {} for job {} — HTTP {}", webhookUrl, jobId, response.statusCode());
        } catch (Exception e) {
            LOG.warn("Webhook failed for job {} → {}: {}", jobId, webhookUrl, e.getMessage());
        }
    }
}
