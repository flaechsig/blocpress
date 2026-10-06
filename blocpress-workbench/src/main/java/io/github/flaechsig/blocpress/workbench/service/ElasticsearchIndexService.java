package io.github.flaechsig.blocpress.workbench.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.flaechsig.blocpress.core.odt.OdtTextExtractor;
import io.github.flaechsig.blocpress.workbench.entity.Template;
import io.github.flaechsig.blocpress.workbench.entity.ValidationResult;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Sort;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.jboss.logging.Logger;

import io.quarkus.panache.common.Sort;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the Elasticsearch index for full-text search over templates and Bausteine.
 *
 * <p>All operations are best-effort: if Elasticsearch is unavailable, a warning is logged
 * but no exception is thrown and the database transaction is not affected.</p>
 *
 * <p>US-0043: Changes are written after the database transaction has committed
 * ({@link #indexAfterCommit}, {@link #deleteAfterCommit}), so the index never shows a state the
 * database rolled back. If the index is missing (new or lost Elasticsearch data), it is created
 * and rebuilt from the database ({@link #checkIndex}, {@link #rebuild}).</p>
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

    @Inject
    TransactionSynchronizationRegistry transactions;

    @ConfigProperty(name = "elasticsearch.index.name", defaultValue = "blocpress-templates")
    String indexName;

    private final OdtTextExtractor textExtractor = new OdtTextExtractor();

    /** Index nach dem letzten Pruefen vorhanden; vermeidet ein HEAD vor jedem Schreiben. */
    private volatile boolean indexKnown;

    /**
     * Index muss aus der Datenbank neu aufgebaut werden: er wurde leer angelegt, oder ein
     * Schreiben oder Loeschen ist gescheitert (Elasticsearch nicht erreichbar).
     */
    private volatile boolean rebuildNeeded;

    /**
     * Legt den Index an, wenn er fehlt, und merkt dann einen Neuaufbau vor. Ohne Elasticsearch
     * passiert nichts; der naechste Aufruf versucht es erneut.
     */
    void ensureIndexExists() {
        if (indexKnown) return;
        synchronized (this) {
            if (indexKnown) return;
            try {
                Response response = restClient.performRequest(new Request("HEAD", "/" + indexName));
                if (response.getStatusLine().getStatusCode() == 404) {
                    createIndex();
                    rebuildNeeded = true;
                }
                indexKnown = true;
            } catch (Exception e) {
                LOG.warnf("Could not check/create Elasticsearch index '%s': %s", indexName, e.getMessage());
            }
        }
    }

    /**
     * Prueft regelmaessig (und kurz nach dem Start), ob der Index existiert, und baut ihn aus der
     * Datenbank neu auf, wenn er fehlte oder Aenderungen nicht geschrieben werden konnten, etwa
     * nach Verlust der Elasticsearch-Daten, einem Ausfall oder wenn Elasticsearch erst nach der
     * Workbench startet.
     */
    @Scheduled(every = "${blocpress.search.index-check:60s}", delayed = "${blocpress.search.index-check-delay:5s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void checkIndex() {
        indexKnown = false;
        ensureIndexExists();
        if (indexKnown && rebuildNeeded) {
            int count = rebuild();
            LOG.infof("Elasticsearch index '%s' has been rebuilt with %d template(s).", indexName, count);
        }
    }

    /**
     * Baut den Index neu auf: loescht ihn, legt ihn an und indiziert alle Vorlagen und Bausteine
     * aus der Datenbank.
     *
     * @return Zahl der indizierten Vorlagen und Bausteine
     */
    public synchronized int rebuild() {
        try {
            restClient.performRequest(new Request("DELETE", "/" + indexName));
        } catch (Exception e) {
            LOG.debugf("Index '%s' could not be deleted before rebuild: %s", indexName, e.getMessage());
        }
        indexKnown = false;
        ensureIndexExists();
        rebuildNeeded = false;
        return indexAll();
    }

    /**
     * Indiziert alle Vorlagen und Bausteine seitenweise; die Dokumente einer Seite entstehen in
     * einer eigenen Lese-Transaktion (der Inhalt wird lazy geladen).
     *
     * @return Zahl der erfolgreich indizierten Vorlagen und Bausteine
     */
    private int indexAll() {
        int count = 0;
        for (int page = 0; ; page++) {
            final int p = page;
            Map<UUID, ObjectNode> docs = QuarkusTransaction.requiringNew().call(() -> {
                Map<UUID, ObjectNode> result = new LinkedHashMap<>();
                Template.<Template>findAll(Sort.by("id")).page(p, 50).list()
                        .forEach(t -> result.put(t.id, toDocument(t)));
                return result;
            });
            if (docs.isEmpty()) {
                return count;
            }
            for (var entry : docs.entrySet()) {
                if (put(entry.getKey(), entry.getValue())) {
                    count++;
                }
            }
        }
    }

    /**
     * Indiziert die Vorlage, sobald die laufende Transaktion erfolgreich abgeschlossen ist; ohne
     * Transaktion sofort. Bei einem Rollback bleibt der Index unveraendert.
     */
    public void indexAfterCommit(Template template) {
        // Dokument jetzt bauen, solange die Session offen ist; senden erst nach dem Commit
        ObjectNode doc = toDocument(template);
        UUID id = template.id;
        afterCommit(() -> put(id, doc));
    }

    /** Entfernt die Vorlage aus dem Index, sobald die laufende Transaktion erfolgreich abgeschlossen ist. */
    public void deleteAfterCommit(UUID templateId) {
        afterCommit(() -> delete(templateId));
    }

    private void afterCommit(Runnable action) {
        if (transactions.getTransactionKey() == null) {
            action.run();
            return;
        }
        transactions.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) {
                    action.run();
                }
            }
        });
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
     * Indexes or updates a template document in Elasticsearch. Must run while the template's
     * session is open, because the content is loaded lazily.
     */
    public void index(Template template) {
        put(template.id, toDocument(template));
    }

    /**
     * Baut das Index-Dokument: Name, Typ, Status, Version, Felder, Bedingungen und Text. Eine
     * nicht lesbare Datei bekommt ein Dokument ohne Text, damit sie mit Name und Status
     * auffindbar bleibt.
     */
    ObjectNode toDocument(Template template) {
        String extractedText = "";
        if (template.content != null) {
            try {
                extractedText = textExtractor.extract(template.content);
            } catch (Exception e) {
                LOG.debugf("No text extracted from template '%s': %s", template.id, e.getMessage());
            }
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
        return doc;
    }

    /** Schreibt ein Index-Dokument; gibt zurück, ob es gelang. */
    private boolean put(UUID templateId, ObjectNode doc) {
        ensureIndexExists();
        try {
            Request req = new Request("PUT", "/" + indexName + "/_doc/" + templateId);
            req.setEntity(new StringEntity(objectMapper.writeValueAsString(doc),
                    ContentType.APPLICATION_JSON));
            restClient.performRequest(req);
            LOG.debugf("Indexed template '%s' (%s)", doc.path("name").asText(), templateId);
            return true;
        } catch (Exception e) {
            LOG.warnf("Failed to index template '%s', index will be rebuilt: %s", templateId, e.getMessage());
            rebuildNeeded = true;
            return false;
        }
    }

    /**
     * Removes a template document from the index.
     */
    public void delete(UUID templateId) {
        try {
            restClient.performRequest(new Request("DELETE", "/" + indexName + "/_doc/" + templateId));
            LOG.debugf("Deleted template '%s' from index", templateId);
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() != 404) {  // 404: war nie indiziert
                LOG.warnf("Failed to delete template '%s' from Elasticsearch, index will be rebuilt: %s",
                        templateId, e.getMessage());
                rebuildNeeded = true;
            }
        } catch (Exception e) {
            LOG.warnf("Failed to delete template '%s' from Elasticsearch, index will be rebuilt: %s",
                    templateId, e.getMessage());
            rebuildNeeded = true;
        }
    }

    /**
     * Executes a full-text search query and returns raw JSON hits.
     */
    public String search(String query, String type, String status, int from, int size) {
        ensureIndexExists();
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