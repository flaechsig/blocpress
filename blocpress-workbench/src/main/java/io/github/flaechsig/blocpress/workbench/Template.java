package io.github.flaechsig.blocpress.workbench;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "template")
public class Template extends PanacheEntityBase {

    @Id
    @GeneratedValue
    public UUID id;

    @Column(nullable = false, unique = true)
    public String name;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.VARBINARY)
    public byte[] content;

    @Column(nullable = false)
    public Instant createdAt;
}
