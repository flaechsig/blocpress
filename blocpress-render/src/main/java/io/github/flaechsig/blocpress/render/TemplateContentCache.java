package io.github.flaechsig.blocpress.render;

import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

/**
 * Inhalt einer Version aus production, zwischengespeichert nach {@code id}. Der Inhalt einer
 * {@code id} aendert sich nicht; welche {@code id} gilt, ermittelt {@link TemplateCache} bei jedem
 * Rendern neu. So wirken Ablauf, Ausmustern und neue Versionen sofort und auf allen Instanzen
 * (REQ-0038, REQ-0063).
 */
@ApplicationScoped
public class TemplateContentCache {

    /** @throws TemplateNotFoundException wenn die Version inzwischen entfernt wurde */
    @Transactional
    @CacheResult(cacheName = "template-content")
    public byte[] content(UUID id) {
        ProductionTemplate template = ProductionTemplate.findById(id);
        if (template == null) {
            throw new TemplateNotFoundException("Template version not found in production: " + id);
        }
        return template.content;
    }

    /** Verwirft den Inhalt dieser {@code id}, etwa wenn sie neu importiert wird. */
    @CacheInvalidate(cacheName = "template-content")
    public void invalidate(UUID id) {
        // Cache eviction is handled by the annotation
    }
}
