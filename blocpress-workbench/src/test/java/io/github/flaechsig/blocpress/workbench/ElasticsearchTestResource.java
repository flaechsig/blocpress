package io.github.flaechsig.blocpress.workbench;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.util.Map;

/**
 * Starts a real Elasticsearch container for SearchIT.
 * The datasource stays as H2 (configured in application-test.properties).
 * H2's MODE=PostgreSQL accepts the "bytea" column definition used on Template.content,
 * so real ODT files can be stored without size limits.
 */
public class ElasticsearchTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String ES_IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:8.11.0";

    private ElasticsearchContainer esContainer;

    @Override
    public Map<String, String> start() {
        esContainer = new ElasticsearchContainer(ES_IMAGE)
                .withEnv("xpack.security.enabled", "false")
                .withEnv("discovery.type", "single-node")
                .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m");
        esContainer.start();

        String esHost = esContainer.getHost() + ":" + esContainer.getMappedPort(9200);
        return Map.of("quarkus.elasticsearch.hosts", esHost);
    }

    @Override
    public void stop() {
        if (esContainer != null) {
            esContainer.stop();
        }
    }
}