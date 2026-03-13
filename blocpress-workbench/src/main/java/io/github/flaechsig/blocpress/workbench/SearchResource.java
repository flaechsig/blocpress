package io.github.flaechsig.blocpress.workbench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.flaechsig.blocpress.workbench.service.ElasticsearchIndexService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.*;

/**
 * REST endpoint for full-text search over templates and Bausteine via Elasticsearch.
 *
 * <p>EDC reference: UC-19 (Nach Begriff suchen), TI-7 (Elasticsearch API)</p>
 */
@Path("/api/workbench/search")
@Produces(MediaType.APPLICATION_JSON)
public class SearchResource {

    @Inject
    ElasticsearchIndexService indexService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Search templates and Bausteine by free text.
     *
     * @param query  search term (required, min 2 chars)
     * @param type   optional filter: TEMPLATE or BAUSTEIN
     * @param status optional filter: DRAFT, SUBMITTED, APPROVED, REJECTED
     * @param from   pagination offset (default 0)
     * @param size   page size (default 20)
     * @return search result with total count and hits including highlighting
     */
    @GET
    public SearchResult search(
            @QueryParam("q")      String query,
            @QueryParam("type")   String type,
            @QueryParam("status") String status,
            @QueryParam("from")   @DefaultValue("0")  int from,
            @QueryParam("size")   @DefaultValue("20") int size) {

        if (query == null || query.isBlank() || query.length() < 2) {
            return new SearchResult(0, List.of());
        }

        String rawResponse = indexService.search(query.trim(), type, status, from, size);
        if (rawResponse == null) {
            return new SearchResult(0, List.of());
        }

        return parseResponse(rawResponse);
    }

    private SearchResult parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode hitsNode = root.path("hits");
            long total = hitsNode.path("total").path("value").asLong(0);

            List<SearchHit> hits = new ArrayList<>();
            for (JsonNode hit : hitsNode.path("hits")) {
                JsonNode src = hit.path("_source");
                String id = src.path("templateId").asText();
                String name = src.path("name").asText();
                String hitType = src.path("type").asText();
                String hitStatus = src.path("status").asText();
                int version = src.path("version").asInt();

                Map<String, List<String>> highlight = new LinkedHashMap<>();
                JsonNode hlNode = hit.path("highlight");
                hlNode.fieldNames().forEachRemaining(field -> {
                    List<String> fragments = new ArrayList<>();
                    hlNode.path(field).forEach(f -> fragments.add(f.asText()));
                    highlight.put(field, fragments);
                });

                hits.add(new SearchHit(UUID.fromString(id), name, hitType, hitStatus, version, highlight));
            }
            return new SearchResult(total, hits);
        } catch (Exception e) {
            return new SearchResult(0, List.of());
        }
    }

    public record SearchResult(long total, List<SearchHit> hits) {}

    public record SearchHit(
            UUID id,
            String name,
            String type,
            String status,
            int version,
            Map<String, List<String>> highlight
    ) {}
}