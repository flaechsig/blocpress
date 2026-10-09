package io.github.flaechsig.blocpress.render;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zeitachse in production: Zurueckziehen beendet die Gueltigkeit, statt zu loeschen (REQ-0037);
 * der Import einer neuen Version beendet die bis dahin gueltige an ihrem Beginn (REQ-0040).
 */
@QuarkusTest
class ProductionTimelineTest {

    private static final Path ODT = Path.of("../blocpress-core/src/test/resources/IfCondition.odt");

    private final HttpClient http = HttpClient.newHttpClient();

    @TestHTTPResource("/")
    URI base;

    @Test
    @DisplayName("REQ-0037: retirementEndsValidityInsteadOfDeleting")
    void retirementEndsValidityInsteadOfDeleting() throws Exception {
        String name = "retired-" + UUID.randomUUID();
        UUID id = importVersion(name, 1, LocalDateTime.now().minusDays(1));
        assertEquals(200, render(name));

        LocalDateTime before = LocalDateTime.now();
        assertEquals(204, http.send(HttpRequest.newBuilder(base.resolve("/api/render/templates/import/" + name))
                .DELETE().build(), HttpResponse.BodyHandlers.discarding()).statusCode());

        ProductionTemplate kept = QuarkusTransaction.requiringNew().call(() -> ProductionTemplate.findById(id));
        assertTrue(kept != null, "die Version muss in production bleiben");
        assertFalse(kept.validUntil.isBefore(before.minusSeconds(1)), "Ende = Zeitpunkt des Zurueckziehens: " + kept.validUntil);
        assertFalse(kept.validUntil.isAfter(LocalDateTime.now()), "Gueltigkeit muss beendet sein: " + kept.validUntil);
        assertEquals(404, render(name), "zurueckgezogene Vorlage darf nicht mehr gerendert werden");
    }

    @Test
    @DisplayName("REQ-0040: importEndsPreviousVersionAtStartOfNewOne")
    void importEndsPreviousVersionAtStartOfNewOne() throws Exception {
        String name = "successor-" + UUID.randomUUID();
        UUID v1 = importVersion(name, 1, LocalDateTime.now().minusDays(1));
        LocalDateTime start = LocalDateTime.now().plusDays(5).withNano(0);
        importVersion(name, 2, start);

        LocalDateTime v1End = QuarkusTransaction.requiringNew().call(
                () -> ProductionTemplate.<ProductionTemplate>findById(v1).validUntil);
        assertEquals(start, v1End, "v1 muss am Beginn von v2 enden");
        List<ProductionTemplate> all = QuarkusTransaction.requiringNew().call(() -> ProductionTemplate.list("name", name));
        assertEquals(2, all.size(), "beide Versionen bleiben erhalten");
    }

    private UUID importVersion(String name, int version, LocalDateTime from) throws Exception {
        UUID id = UUID.randomUUID();
        String body = "{\"id\":\"" + id + "\",\"name\":\"" + name + "\",\"version\":" + version
                + ",\"contentBase64\":\"" + Base64.getEncoder().encodeToString(Files.readAllBytes(ODT))
                + "\",\"validFrom\":\"" + from.withNano(0) + "\"}";
        var response = http.send(HttpRequest.newBuilder(base.resolve("/api/render/templates/import"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return id;
    }

    private int render(String name) throws Exception {
        return http.send(HttpRequest.newBuilder(base.resolve("/api/render/" + name))
                .header("Content-Type", "application/json")
                .header("Accept", "application/vnd.oasis.opendocument.text")
                .POST(HttpRequest.BodyPublishers.ofString("{\"data\":{},\"outputType\":\"odt\"}")).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
