package io.github.flaechsig.blocpress.render;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Cache for template content fetched from the production schema.
 *
 * With TI-2 (multi-schema), templates are imported from blocpress-workbench
 * into the production schema via TemplateImportResource. This cache provides
 * fast access to the local production database.
 *
 * Uses Quarkus Cache with configurable TTL (10 minutes) to minimize database access.
 */
@ApplicationScoped
public class TemplateCache {
    private static final Logger logger = LoggerFactory.getLogger(TemplateCache.class);

    /**
     * Fetches template content by ID from the production schema.
     * Results are cached for performance (10 minutes TTL).
     *
     * @param templateId Template UUID
     * @return Template binary content (ODT file)
     * @throws TemplateNotFoundException if template does not exist in production
     */
    @CacheResult(cacheName = "templates")
    public byte[] getTemplateContent(UUID templateId) {
        logger.info("Fetching template {} from production schema (cache miss)", templateId);

        ProductionTemplate template = ProductionTemplate.findById(templateId);
        if (template == null) {
            throw new TemplateNotFoundException("Template not found in production: " + templateId);
        }

        logger.info("Successfully fetched template {} (size: {} bytes)", templateId, template.content.length);
        return template.content;
    }

    /**
     * Fetches template content by name from the production schema.
     * Retrieves the latest version (highest version number for the given name).
     * Results are cached for performance (10 minutes TTL).
     *
     * @param templateName Template name
     * @return Template binary content (ODT file) of latest version
     * @throws TemplateNotFoundException if template does not exist in production
     */
    @CacheResult(cacheName = "templates")
    public byte[] getTemplateContentByName(String templateName) {
        logger.info("Fetching template {} from production schema (cache miss)", templateName);

        ProductionTemplate template = ProductionTemplate.findLatestActiveByName(templateName);
        if (template == null) {
            throw new TemplateNotFoundException("Template not found in production: " + templateName);
        }

        logger.info("Successfully fetched template {} v{} (size: {} bytes)",
            templateName, template.version, template.content.length);
        return template.content;
    }
}
