package io.github.flaechsig.blocpress.workbench.service;

import io.github.flaechsig.blocpress.workbench.entity.Template;
import io.github.flaechsig.blocpress.workbench.entity.TemplateStatus;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Meldet beim Start, welche Daten die Regeln "ein Name, eine Zeitachse" verletzen (REQ-0043):
 * Namen, die beide Typen benutzen (REQ-0039), Namen mit mehr als einem Entwurf (REQ-0042) und
 * ueberlappende Gueltigkeit freigegebener oder ausgemusterter Versionen (REQ-0040). Aendert nichts;
 * welche Version gilt, entscheidet ein Mensch.
 */
@ApplicationScoped
public class TimelineConflictReport {

    private static final Logger LOG = LoggerFactory.getLogger(TimelineConflictReport.class);

    void onStart(@Observes StartupEvent event) {
        report();
    }

    /** Loggt jeden Konflikt als Warnung und gibt sie zurueck; die Daten bleiben unveraendert. */
    @Transactional
    public List<String> report() {
        List<String> conflicts = new ArrayList<>();
        Template.getEntityManager().createQuery(
                "SELECT t.name FROM Template t GROUP BY t.name HAVING COUNT(DISTINCT t.type) > 1 ORDER BY t.name",
                String.class)
            .getResultList()
            .forEach(name -> conflicts.add("Name '" + name + "' wird von Vorlage und Baustein benutzt"));
        Template.getEntityManager().createQuery(
                "SELECT t.name FROM Template t WHERE t.status IN :drafts GROUP BY t.name HAVING COUNT(t) > 1 ORDER BY t.name",
                String.class)
            .setParameter("drafts", List.of(TemplateStatus.DRAFT, TemplateStatus.SUBMITTED, TemplateStatus.REJECTED))
            .getResultList()
            .forEach(name -> conflicts.add("Name '" + name + "' hat mehr als einen Entwurf"));
        conflicts.addAll(overlaps());

        conflicts.forEach(c -> LOG.warn("Zeitachse: {}", c));
        if (!conflicts.isEmpty()) {
            LOG.warn("Zeitachse: {} Konflikt(e) in bestehenden Daten; bitte von Hand aufloesen, nichts wurde geaendert",
                conflicts.size());
        }
        return conflicts;
    }

    /** Je Name: Versionen nach Beginn sortiert; eine Version, die vor dem Ende einer frueheren beginnt, ueberlappt. */
    private static List<String> overlaps() {
        List<Template> versions = Template.list(
            "status IN ?1 AND validFrom IS NOT NULL ORDER BY name, validFrom, version",
            List.of(TemplateStatus.APPROVED, TemplateStatus.RETIRED));
        List<String> result = new ArrayList<>();
        String name = null;
        Template latestEnding = null;
        for (Template t : versions) {
            if (!t.name.equals(name)) {
                name = t.name;
                latestEnding = t;
                continue;
            }
            if (endsAfter(latestEnding, t.validFrom)) {
                result.add("Name '" + t.name + "': Version " + latestEnding.version + " und Version " + t.version
                    + " gelten gleichzeitig ab " + t.validFrom);
            }
            if (latestEnding.validUntil != null && (t.validUntil == null || t.validUntil.isAfter(latestEnding.validUntil))) {
                latestEnding = t;
            }
        }
        return result;
    }

    private static boolean endsAfter(Template t, LocalDateTime moment) {
        return t.validUntil == null || t.validUntil.isAfter(moment);
    }
}
