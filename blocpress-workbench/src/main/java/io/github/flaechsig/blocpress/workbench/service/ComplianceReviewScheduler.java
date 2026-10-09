package io.github.flaechsig.blocpress.workbench.service;

import io.github.flaechsig.blocpress.workbench.entity.Template;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * UC-12: Täglicher Compliance-Check für ablaufende Templates.
 * Loggt alle APPROVED-Templates, deren validUntil innerhalb des konfigurierten
 * Vorlaufzeitraums liegt.
 */
@ApplicationScoped
public class ComplianceReviewScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ComplianceReviewScheduler.class);

    @ConfigProperty(name = "blocpress.compliance.review-lead-days", defaultValue = "60")
    int leadDays;

    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional
    public void checkDueForReview() {
        LocalDateTime threshold = LocalDateTime.now().plusDays(leadDays);
        List<Template> due = Template.list(
            "FROM Template t WHERE t.status = 'APPROVED' AND t.validUntil IS NOT NULL AND t.validUntil <= ?1"
                // abgeloeste Versionen (es gibt eine neuere freigegebene) sind nicht faellig (REQ-0040)
                + " AND NOT EXISTS (FROM Template n WHERE n.name = t.name AND n.status = 'APPROVED'"
                + " AND n.version > t.version)",
                threshold);
        if (!due.isEmpty()) {
            LOG.warn("{} Template(s) laufen innerhalb von {} Tagen ab: {}",
                due.size(), leadDays, due.stream().map(t -> t.name).toList());
        }
    }
}
