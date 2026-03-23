package io.github.flaechsig.blocpress.render;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    /** Ablaufdatum des Templates. Null = kein Ablauf. */
    @Column(name = "valid_until")
    public LocalDateTime validUntil;

    /**
     * Finds the currently active template by name.
     * Returns the template with the highest version for the most recent valid_from <= now(),
     * excluding expired templates (valid_until < now()).
     *
     * @param name Template name
     * @return Currently active template, or null if not found or expired
     */
    public static ProductionTemplate findLatestActiveByName(String name) {
        return find("""
            FROM ProductionTemplate
            WHERE name = ?1
            AND validFrom <= CURRENT_TIMESTAMP
            AND (validUntil IS NULL OR validUntil > CURRENT_TIMESTAMP)
            ORDER BY validFrom DESC, version DESC
            """, name).firstResult();
    }
}
