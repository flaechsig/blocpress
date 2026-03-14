package io.github.flaechsig.blocpress.render;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Job-Entity für asynchrones Rendering.
 * Nutzt PostgreSQL SKIP LOCKED für nebenläufig-sichere Job-Verteilung.
 */
@Entity
@Table(name = "render_job")
public class RenderJob extends PanacheEntityBase {

    @Id
    public UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public RenderJobStatus status = RenderJobStatus.PENDING;

    @Column(name = "template_name")
    public String templateName;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    public String data;

    @Column(name = "output_type", nullable = false)
    public String outputType = "pdf";

    @Column(columnDefinition = "bytea")
    @JdbcTypeCode(SqlTypes.VARBINARY)
    public byte[] result;

    @Column(name = "webhook_url")
    public String webhookUrl;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "error_message")
    public String errorMessage;

    /**
     * Holt den ältesten PENDING-Job und setzt ihn atomar auf PROCESSING.
     * Nutzt SKIP LOCKED damit mehrere Worker gleichzeitig arbeiten können.
     *
     * @return Job oder null wenn nichts wartet
     */
    @SuppressWarnings("unchecked")
    public static RenderJob claimNextPending() {
        var results = getEntityManager()
                .createNativeQuery(
                        "UPDATE render_job SET status = 'PROCESSING', updated_at = now() " +
                        "WHERE id = (SELECT id FROM render_job WHERE status = 'PENDING' " +
                        "            ORDER BY created_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED) " +
                        "RETURNING *",
                        RenderJob.class)
                .getResultList();
        return results.isEmpty() ? null : (RenderJob) results.get(0);
    }
}
