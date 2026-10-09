package io.github.flaechsig.blocpress.workbench.service;

import io.github.flaechsig.blocpress.core.RenderEngine;
import io.github.flaechsig.blocpress.core.TemplateDocument;
import io.github.flaechsig.blocpress.core.TextBlockRejectedException;
import io.github.flaechsig.blocpress.core.TextBlockResolver;
import io.github.flaechsig.blocpress.workbench.entity.Template;
import io.github.flaechsig.blocpress.workbench.entity.TemplateStatus;
import io.github.flaechsig.blocpress.workbench.entity.TemplateType;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Setzt fuer Vorschau und Regression die verknuepften Bausteine aus der Workbench ein (ADR-0018,
 * REQ-0045): den Entwurf, falls es einen gibt, sonst die gueltige freigegebene Version. render
 * nimmt mitgeschickte Vorlagen mit Verknuepfungen nicht an (REQ-0035).
 */
@ApplicationScoped
public class WorkbenchTextBlocks {

    /**
     * @return die Vorlage ohne Verknuepfungen; unveraendert, wenn sie keine hat
     * @throws TextBlockRejectedException wenn ein verknuepfter Baustein fehlt
     */
    public byte[] inline(byte[] templateContent) throws Exception {
        if (!hasLinks(templateContent)) {
            return templateContent;
        }
        Path tmp = Files.createTempFile("workbench-preview", ".odt");
        try {
            Files.write(tmp, templateContent);
            return RenderEngine.expandTextBlocks(tmp.toUri().toURL(), TextBlockResolver.byName(WorkbenchTextBlocks::content));
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static boolean hasLinks(byte[] content) {
        return TemplateDocument.load(content).collectIncludedTextBlocks().stream()
                .anyMatch(s -> s.getHref() != null && !s.getHref().isBlank());
    }

    private static byte[] content(String name) {
        Template draft = Template.<Template>find(
                "name = ?1 AND type = ?2 AND status = ?3 ORDER BY version DESC",
                name, TemplateType.BAUSTEIN, TemplateStatus.DRAFT).firstResult();
        if (draft != null) {
            return draft.content;
        }
        LocalDateTime now = LocalDateTime.now();
        Template approved = Template.<Template>find(
                "name = ?1 AND type = ?2 AND status = ?3 AND (validFrom IS NULL OR validFrom <= ?4)"
                        + " AND (validUntil IS NULL OR validUntil > ?4) ORDER BY version DESC",
                name, TemplateType.BAUSTEIN, TemplateStatus.APPROVED, now).firstResult();
        return approved == null ? null : approved.content;
    }
}
