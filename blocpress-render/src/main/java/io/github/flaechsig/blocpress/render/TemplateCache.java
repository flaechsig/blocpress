package io.github.flaechsig.blocpress.render;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Liefert den Inhalt der gueltigen Vorlage bzw. des gueltigen Bausteins eines Namens aus production.
 *
 * <p>Welche Version gilt, wird bei jedem Aufruf aus der Datenbank ermittelt (eine kleine Abfrage
 * ohne Inhalt); nur der Inhalt liegt nach {@code id} im Cache ({@link TemplateContentCache}).
 * Damit wirken Ablauf, Ausmustern und neue Versionen sofort, auch wenn eine andere render-Instanz
 * die Aenderung angenommen hat (REQ-0038, REQ-0063).</p>
 */
@ApplicationScoped
public class TemplateCache {
    private static final Logger logger = LoggerFactory.getLogger(TemplateCache.class);

    @Inject
    TemplateContentCache contentCache;

    /**
     * @return Inhalt (ODT) der jetzt gueltigen Vorlage dieses Namens
     * @throws TemplateNotFoundException wenn keine Version gilt
     */
    @Transactional
    public byte[] getTemplateContentByName(String templateName) {
        UUID id = ProductionTemplate.findValidId(templateName, TemplateType.TEMPLATE);
        if (id == null) {
            throw new TemplateNotFoundException("Template not found in production: " + templateName);
        }
        logger.debug("Template {} -> version {}", templateName, id);
        return contentCache.content(id);
    }

    /**
     * Liefert den gueltigen, freigegebenen Baustein dieses Namens (ADR-0018, REQ-0033).
     *
     * @throws TemplateNotFoundException wenn es keinen gibt
     */
    @Transactional
    public byte[] getBausteinContentByName(String name) {
        UUID id = ProductionTemplate.findValidId(name, TemplateType.BAUSTEIN);
        if (id == null) {
            throw new TemplateNotFoundException("Building block not found in production: " + name);
        }
        logger.debug("Building block {} -> version {}", name, id);
        return contentCache.content(id);
    }
}
