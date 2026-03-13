package io.github.flaechsig.blocpress.workbench.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.flaechsig.blocpress.core.odt.OdtTextExtractor;
import io.github.flaechsig.blocpress.workbench.entity.Template;
import io.github.flaechsig.blocpress.workbench.entity.ValidationResult;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manages the Elasticsearch index for full-text search over templates and Bausteine.
 *
 * <p>All operations are best-effort: if Elasticsearch is unavailable, a warning is logged
 * but no exception is thrown and the database transaction is not affected.</p>
 *
 * <p>EDC reference: TI-7 (Elasticsearch API), UC-19 (Nach Begriff suchen)</p>
 */
@ApplicationScoped
public class ElasticsearchIndexService {

    private static final Logger LOG = Logger.getLogger(ElasticsearchIndexService.class);

    @Inject
    RestClient restClient;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "elasticsearch.index.name", defaultValue = "blocpress-templates")
    String indexName;

    private final OdtTextExtractor textExtractor = new OdtTextExtractor();

    @PostConstruct
    void ensureIndex() {
        try {
            Response response = restClient.performRequest(new Request("HEAD", "/" + indexName));
            if (response.getStatusLine().getStatusCode() == 404) {
                createIndex();
            }
        } catch (Exception e) {
            LOG.warnf("Could not check/create Elasticsearch index '%s': %s", indexName, e.getMessage());
            try {
                createIndex();
            } catch (Exception ex) {
                LOG.warnf("Elasticsearch index creation failed (ES may not be running): %s", ex.getMessage());
            }
        }
    }

    private void createIndex() throws Exception {
        String mapping = """
                {
                  "settings": {
                    "analysis": {
                      "analyzer": { "german_standard": { "type": "german" } }
                    }
                  },
                  "mappings": {
                    "properties": {
                      "templateId":    { "type": "keyword" },
                      "name":          { "type": "text", "analyzer": "german",
                                         "fields": { "keyword": { "type": "keyword" } } },
                      "type":          { "type": "keyword" },
                      "status":        { "type": "keyword" },
                      "version":       { "type": "integer" },
                      "fieldNames":    { "type": "text", "analyzer": "standard" },
                      "conditions":    { "type": "text", "analyzer": "standard" },
                      "extractedText": { "type": "text", "analyzer": "german" },
                      "updatedAt":     { "type": "date" }
                    }
                  }
                }
                """;
        Request req = new Request("PUT", "/" + indexName);
        req.setEntity(new StringEntity(mapping, ContentType.APPLICATION_JSON));
        restClient.performRequest(req);
        LOG.infof("Elasticsearch index '%s' created.", indexName);
    }

    /**
     * Indexes or updates a template document in Elasticsearch.
     * Extracts plain text from ODT content for full-text search.
     */
    public void index(Template template) {
        try {
            String extractedText = "";
            if (template.content != null) {
                extractedText = textExtractor.extract(template.content);
            }

            List<String> fieldNames = List.of();
            List<String> conditions = List.of();
            if (template.validationResult != null) {
                if (template.validationResult.schema() != null) {
                    fieldNames = extractFieldNames(template.validationResult);
                }
                if (template.validationResult.conditions() != null) {
                    conditions = template.validationResult.conditions();
                }
            }

            ObjectNode doc = objectMapper.createObjectNode();
            doc.put("templateId", template.id.toString());
            doc.put("name", template.name);
            doc.put("type", template.type.name());
            doc.put("status", template.status.name());
            doc.put("version", template.version);
            doc.put("extractedText", extractedText);
            doc.put("updatedAt", Instant.now().toString());
            doc.set("fieldNames", objectMapper.valueToTree(fieldNames));
            doc.set("conditions", objectMapper.valueToTree(conditions));

            Request req = new Request("PUT", "/" + indexName + "/_doc/" + template.id);
            req.setEntity(new StringEntity(objectMapper.writeValueAsString(doc),
                    ContentType.APPLICATION_JSON));
            restClient.performRequest(req);
            LOG.debugf("Indexed template '%s' (%s)", template.name, template.id);
        } catch (Exception e) {
            LOG.warnf("Failed to index template '%s': %s", template.id, e.getMessage());
        }
    }

    /**
     * Updates only the status field of an existing index document.
     */
    public void updateStatus(UUID templateId, String newStatus) {
        try {
            String body = """
                    { "doc": { "status": "%s", "updatedAt": "%s" } }
                    """.formatted(newStatus, Instant.now());
            Request req = new Request("POST", "/" + indexName + "/_update/" + templateId);
            req.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
            restClient.performRequest(req);
            LOG.debugf("Updated status for template '%s' → %s", templateId, newStatus);
        } catch (Exception e) {
            LOG.warnf("Failed to update status in Elasticsearch for '%s': %s", templateId, e.getMessage());
        }
    }

    /**
     * Removes a template document from the index.
     */
    public void delete(UUID templateId) {
        try {
            restClient.performRequest(new Request("DELETE", "/" + indexName + "/_doc/" + templateId));
            LOG.debugf("Deleted template '%s' from index", templateId);
        } catch (Exception e) {
            LOG.warnf("Failed to delete template '%s' from Elasticsearch: %s", templateId, e.getMessage());
        }
    }

    /**
     * Executes a full-text search query and returns raw JSON hits.
     */
    public String search(String query, String type, String status, int from, int size) {
        try {
            // Combine fuzzy multi_match (handles full words + typos) with phrase-prefix
            // (handles prefix/typeahead: "bloc" → "blocpress", "pay" → "Payment Terms")
            var should = objectMapper.createArrayNode();
            should.add(buildMultiMatch(query));
            should.add(buildPhrasePrefix(query, "name", 5));
            should.add(buildPhrasePrefix(query, "extractedText", 1));

            ObjectNode bool = objectMapper.createObjectNode();
            bool.set("should", should);
            bool.put("minimum_should_match", 1);

            if (type != null || status != null) {
                var filters = objectMapper.createArrayNode();
                if (status != null) {
                    filters.add(objectMapper.createObjectNode()
                            .set("term", objectMapper.createObjectNode().put("status", status)));
                }
                if (type != null) {
                    filters.add(objectMapper.createObjectNode()
                            .set("term", objectMapper.createObjectNode().put("type", type)));
                }
                bool.set("filter", filters);
            }

            ObjectNode highlight = objectMapper.createObjectNode();
            highlight.set("pre_tags", objectMapper.createArrayNode().add("<mark>"));
            highlight.set("post_tags", objectMapper.createArrayNode().add("</mark>"));
            ObjectNode hlFields = objectMapper.createObjectNode();
            hlFields.set("name", objectMapper.createObjectNode());
            ObjectNode extractedTextHl = objectMapper.createObjectNode();
            extractedTextHl.put("fragment_size", 150);
            extractedTextHl.put("number_of_fragments", 2);
            hlFields.set("extractedText", extractedTextHl);
            highlight.set("fields", hlFields);

            ObjectNode queryBody = objectMapper.createObjectNode();
            queryBody.put("from", from);
            queryBody.put("size", size);
            queryBody.set("query", objectMapper.createObjectNode().set("bool", bool));
            queryBody.set("highlight", highlight);

            Request req = new Request("GET", "/" + indexName + "/_search");
            req.setEntity(new StringEntity(objectMapper.writeValueAsString(queryBody),
                    ContentType.APPLICATION_JSON));
            Response response = restClient.performRequest(req);
            return EntityUtils.toString(response.getEntity());
        } catch (Exception e) {
            LOG.warnf("Elasticsearch search failed: %s", e.getMessage());
            return null;
        }
    }

    private ObjectNode buildMultiMatch(String query) {
        ObjectNode multiMatch = objectMapper.createObjectNode();
        multiMatch.put("query", query);
        multiMatch.set("fields", objectMapper.createArrayNode()
                .add("name^3").add("fieldNames^2").add("conditions").add("extractedText"));
        multiMatch.put("fuzziness", "AUTO");
        return objectMapper.createObjectNode().set("multi_match", multiMatch);
    }

    /** Phrase-prefix query for typeahead / prefix matching on a single field. */
    private ObjectNode buildPhrasePrefix(String query, String field, int boost) {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("query", query);
        params.put("boost", boost);
        ObjectNode inner = objectMapper.createObjectNode();
        inner.set(field, params);
        return objectMapper.createObjectNode().set("match_phrase_prefix", inner);
    }

    private List<String> extractFieldNames(ValidationResult vr) {
        if (vr.schema() == null || !vr.schema().has("properties")) return List.of();
        var props = vr.schema().get("properties");
        var names = new java.util.ArrayList<String>();
        props.fieldNames().forEachRemaining(names::add);
        return names;
    }
}