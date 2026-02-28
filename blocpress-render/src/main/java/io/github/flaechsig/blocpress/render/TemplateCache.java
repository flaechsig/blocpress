package io.github.flaechsig.blocpress.render;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * Cache for template content fetched from blocpress-workbench.
 *
 * Uses Quarkus Cache with configurable TTL to minimize cross-module API calls.
 * When TI-2 (multi-schema) is implemented, this will be replaced with direct
 * database access to the 'production' schema.
 */
@ApplicationScoped
public class TemplateCache {
    private static final Logger logger = LoggerFactory.getLogger(TemplateCache.class);

    @ConfigProperty(name = "blocpress.workbench.url", defaultValue = "http://localhost:8081")
    String workbenchUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Fetches template content by ID from blocpress-workbench.
     * Results are cached for performance.
     *
     * @param templateId Template UUID
     * @return Template binary content (ODT file)
     * @throws TemplateNotFoundException if template does not exist or is not APPROVED
     * @throws IOException if HTTP request fails
     */
    @CacheResult(cacheName = "templates")
    public byte[] getTemplateContent(UUID templateId) throws IOException, InterruptedException {
        logger.info("Fetching template {} from blocpress-workbench (cache miss)", templateId);

        String url = workbenchUrl + "/api/workbench/templates/" + templateId + "/content";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 404) {
            throw new TemplateNotFoundException("Template not found: " + templateId);
        } else if (response.statusCode() == 403) {
            throw new TemplateNotFoundException("Template not approved: " + templateId);
        } else if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch template: HTTP " + response.statusCode());
        }

        logger.info("Successfully fetched template {} (size: {} bytes)", templateId, response.body().length);
        return response.body();
    }

    /**
     * Fetches template content by name from blocpress-workbench.
     * Retrieves the latest active version (validFrom <= now).
     * Results are cached for performance.
     *
     * @param templateName Template name
     * @return Template binary content (ODT file) of latest active version
     * @throws TemplateNotFoundException if template does not exist or no active version is APPROVED
     * @throws IOException if HTTP request fails
     */
    @CacheResult(cacheName = "templates")
    public byte[] getTemplateContentByName(String templateName) throws IOException, InterruptedException {
        logger.info("Fetching template {} from blocpress-workbench (cache miss)", templateName);

        String url = workbenchUrl + "/api/workbench/templates/by-name/" + templateName + "/content";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 404) {
            throw new TemplateNotFoundException("Template not found or no active version: " + templateName);
        } else if (response.statusCode() == 403) {
            throw new TemplateNotFoundException("Template not approved: " + templateName);
        } else if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch template: HTTP " + response.statusCode());
        }

        logger.info("Successfully fetched template {} (size: {} bytes)", templateName, response.body().length);
        return response.body();
    }
}
