package io.github.flaechsig.blocpress.render;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Fetches template content by name from the production schema.
     * Retrieves the latest version (highest version number for the given name).
     * Results are cached for performance (10 minutes TTL).
     *
     * @param templateName Template name
     * @return Template binary content (ODT file) of latest version
     * @throws TemplateNotFoundException if template does not exist in production
     */
    @Transactional
    @CacheResult(cacheName = "templates")
    public byte[] getTemplateContentByName(String templateName) {
        logger.info("Fetching template {} from production schema (cache miss)", templateName);

        ProductionTemplate template = ProductionTemplate.findLatestActiveByName(templateName, TemplateType.TEMPLATE);
        if (template == null) {
            throw new TemplateNotFoundException("Template not found in production: " + templateName);
        }

        logger.info("Successfully fetched template {} v{} (size: {} bytes)",
            templateName, template.version, template.content.length);
        return template.content;
    }

    /**
     * Liefert den gueltigen, freigegebenen Baustein dieses Namens (ADR-0018, REQ-0033).
     *
     * @throws TemplateNotFoundException wenn es keinen gibt
     */
    @Transactional
    @CacheResult(cacheName = "bausteine")
    public byte[] getBausteinContentByName(String name) {
        ProductionTemplate baustein = ProductionTemplate.findLatestActiveByName(name, TemplateType.BAUSTEIN);
        if (baustein == null) {
            throw new TemplateNotFoundException("Building block not found in production: " + name);
        }
        logger.info("Fetched building block {} v{}", name, baustein.version);
        return baustein.content;
    }

    /** Invalidiert den Baustein-Eintrag dieses Namens. */
    @CacheInvalidate(cacheName = "bausteine")
    public void invalidateBaustein(String name) {
        // Cache eviction is handled by the annotation
    }

    /**
     * Invalidates the cache entry for a specific template name.
     * Called when a template is removed from production (RETIRED transition).
     *
     * @param templateName Template name whose cache entry should be invalidated
     */
    @CacheInvalidate(cacheName = "templates")
    public void invalidate(String templateName) {
        // Cache eviction is handled by the annotation
    }
}
