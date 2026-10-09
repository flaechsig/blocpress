package io.github.flaechsig.blocpress.render;

import io.github.flaechsig.blocpress.core.TextBlockResolver;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Bausteine aus der eigenen Produktions-DB (ADR-0018, REQ-0033): render nimmt aus der
 * Verknuepfung nur den Namen und oeffnet sie nie.
 */
@ApplicationScoped
public class ProductionTextBlocks {

    @Inject
    TemplateCache templateCache;

    /** Regel fuer Vorlagen aus production (Rendern per Namen, Jobs). */
    public TextBlockResolver resolver() {
        return TextBlockResolver.byName(name -> {
            try {
                // eigene Transaktion: der Worker-Thread der Jobs hat keinen Request-Kontext
                return QuarkusTransaction.requiringNew().call(() -> templateCache.getBausteinContentByName(name));
            } catch (TemplateNotFoundException e) {
                return null;
            }
        });
    }
}
