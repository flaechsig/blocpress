package io.github.flaechsig.blocpress.workbench;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "template", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "version"}))
public class Template extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public Integer version = 1;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.VARBINARY)
    public byte[] content;

    @Column(nullable = false)
    public Instant createdAt;

    @Column(nullable = true)
    public LocalDateTime validFrom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TemplateStatus status = TemplateStatus.DRAFT;

    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    public ValidationResult validationResult;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<TestDataSet> testDataSets = new ArrayList<>();

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
