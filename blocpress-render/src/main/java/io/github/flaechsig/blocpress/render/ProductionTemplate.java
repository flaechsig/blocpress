package io.github.flaechsig.blocpress.render;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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

    @Column(nullable = false)
    public Integer version;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.VARBINARY)
    public byte[] content;

    @Column(name = "valid_from")
    public LocalDateTime validFrom;

    /**
     * Finds the latest active (highest version) template by name.
     * Used for /api/render/{name} endpoint.
     *
     * @param name Template name
     * @return Latest version of template, or null if not found
     */
    public static ProductionTemplate findLatestActiveByName(String name) {
        return find("name = ?1 ORDER BY version DESC", name).firstResult();
    }
}
