package io.github.flaechsig.blocpress.workbench;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TestDataSetRepository implements PanacheRepository<TestDataSet> {

    public List<TestDataSet> findByTemplateId(UUID templateId) {
        return find("template.id = ?1", templateId).list();
    }

    public Optional<TestDataSet> findByTemplateIdAndName(UUID templateId, String name) {
        return find("template.id = ?1 and name = ?2", templateId, name).firstResultOptional();
    }

    public long countByTemplateId(UUID templateId) {
        return count("template.id = ?1", templateId);
    }

    public void deleteByTemplateId(UUID templateId) {
        delete("template.id = ?1", templateId);
    }
}
