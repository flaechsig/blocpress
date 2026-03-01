package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "test_data_set")
public class TestDataSet extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    public Template template;

    @Column(nullable = false)
    public String name;

    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    public JsonNode testData;

    @Column(columnDefinition = "bytea", nullable = true)
    @JdbcTypeCode(SqlTypes.BINARY)
    public byte[] expectedPdf;

    @Column(length = 64)
    public String pdfHash;

    @Column(nullable = false)
    public Instant createdAt;

    @Column
    public Instant updatedAt;

    public TestDataSet() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }

    public static TestDataSet create(Template template, String name, JsonNode testData) {
        TestDataSet tds = new TestDataSet();
        tds.template = template;
        tds.name = name;
        tds.testData = testData;
        return tds;
    }
}
