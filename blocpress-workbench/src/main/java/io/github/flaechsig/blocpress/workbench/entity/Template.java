package io.github.flaechsig.blocpress.workbench.entity;

import io.github.flaechsig.blocpress.workbench.entity.TestDataSet;
import io.github.flaechsig.blocpress.workbench.entity.TemplateStatus;
import io.github.flaechsig.blocpress.workbench.entity.TemplateType;
import io.github.flaechsig.blocpress.workbench.entity.ValidationResult;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "template", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "valid_from", "version"}))
public class Template extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false)
    public String name;

    @Column(name = "valid_from", nullable = false)
    public LocalDateTime validFrom = LocalDateTime.now();

    @Column(nullable = false)
    public Integer version = 1;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "bytea")
    @JdbcTypeCode(SqlTypes.VARBINARY)
    public byte[] content;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TemplateStatus status = TemplateStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'TEMPLATE'")
    public TemplateType type = TemplateType.TEMPLATE;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "validation_result", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    public ValidationResult validationResult;

    /** Regex-Muster für Textblöcke, die beim Regressionsvergleich ignoriert werden sollen. */
    @Column(name = "ignored_patterns", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    public List<String> ignoredPatterns = new ArrayList<>();

    /** Ablaufdatum des Templates (berechnet: validFrom + reviewCycleYears). Null = kein Ablauf. */
    @Column(name = "valid_until")
    public LocalDateTime validUntil;

    /** Reviewzeitraum in Jahren (gesetzt bei APPROVED). Null = unbegrenzt. */
    @Column(name = "review_cycle_years")
    public Integer reviewCycleYears;

    /** Begründung für die Ablehnung (gesetzt bei SUBMITTED → REJECTED). */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    public String rejectionReason;

    /** Zeitpunkt der Ablehnung. */
    @Column(name = "rejected_at")
    public LocalDateTime rejectedAt;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<TestDataSet> testDataSets = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Finds the latest active version of a template by name.
     * Returns the highest version where validFrom <= asOfDate and status = APPROVED.
     *
     * @param name Template name
     * @param asOfDate Reference date (typically now)
     * @return Template with latest valid version, or null if not found/not approved
     */
    public static Template findLatestActiveByName(String name, LocalDateTime asOfDate) {
        return find(
            "name = ?1 AND status = 'APPROVED' AND (validFrom IS NULL OR validFrom <= ?2) ORDER BY version DESC",
            name,
            asOfDate
        ).firstResult();
    }

    /**
     * Finds the latest active version of a template by name as of now.
     */
    public static Template findLatestActiveByName(String name) {
        return findLatestActiveByName(name, LocalDateTime.now());
    }
}
