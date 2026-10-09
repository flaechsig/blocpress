package io.github.flaechsig.blocpress.render;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Template entity in the production schema.
 * All templates here are implicitly APPROVED and ready for rendering.
 * No status field needed — if it exists here, it's approved.
 */
@Entity
@Table(name = "template")
public class ProductionTemplate extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String name;

    @Column(name = "valid_from", nullable = false)
    public LocalDateTime validFrom;

    @Column(nullable = false)
    public Integer version;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.VARBINARY)
    public byte[] content;

    /** Vorlage oder Baustein (ADR-0018, REQ-0036). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @ColumnDefault("'TEMPLATE'")
    public TemplateType type = TemplateType.TEMPLATE;

    /** Ablaufdatum des Templates. Null = kein Ablauf. */
    @Column(name = "valid_until")
    public LocalDateTime validUntil;

    /**
     * Ermittelt die {@code id} der jetzt gueltigen Version: {@code validFrom} erreicht,
     * {@code validUntil} nicht ueberschritten, bei mehreren die juengste {@code validFrom}, dann die
     * hoechste Version. Laedt den Inhalt nicht; er liegt nach {@code id} im Cache (REQ-0038, REQ-0063).
     * "Jetzt" ist die Zeit der JVM, wie beim Setzen von {@code validFrom} in der Workbench.
     *
     * @return die {@code id}, oder {@code null}, wenn keine Version gilt
     */
    public static UUID findValidId(String name, TemplateType type) {
        return getEntityManager().createQuery("""
            SELECT t.id FROM ProductionTemplate t
            WHERE t.name = :name
            AND t.type = :type
            AND t.validFrom <= :now
            AND (t.validUntil IS NULL OR t.validUntil > :now)
            ORDER BY t.validFrom DESC, t.version DESC
            """, UUID.class)
            .setParameter("name", name)
            .setParameter("type", type)
            .setParameter("now", LocalDateTime.now())
            .setMaxResults(1)
            .getResultStream()
            .findFirst()
            .orElse(null);
    }
}
