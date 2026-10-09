package io.github.flaechsig.blocpress.render;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.UUID;

import io.smallrye.jwt.build.Jwt;

/** Kleiner HTTP-Client fuer die JWT-Tests (ADR-002) gegen die laufende Quarkus-Testinstanz. */
final class AuthTestClient {

    /** Public Key des Dev-Schluesselpaars (dev-privatekey.pem), Issuer https://blocpress.dev. */
    static final String DEV_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsKU92I7dzo/eEOyQtS3KLZ3D8KEpzvClAWjzzzXck2WQV+QTFH6+meHOetww5ZWCfRT8B1LU4A9buMxY8OuXQCnEP5vYCeFmYz7JCXHEceprpQbBm7CRrwUzJNJxW9cCl+bG/JrGYUV9rgW6zWSYFFel49DGQH0BUovDoeqD70bWRt9sP76mRl97ELk7wABPSare9L3UiWhDnQVn+mabOG9wNFRui/8awRmvOoV1kY3M1gbz34sn3Kx7K0kEip1LBbt5xeVnv3N7WDiP52OtSByqqX180jLnaRobTGu2Zt6gZGa1LHbydWOm+CH6TuBFL4NLB/YvUypYl/Xty8DQfQIDAQAB";
    static final String DEV_ISSUER = "https://blocpress.dev";

    private final HttpClient http = HttpClient.newHttpClient();
    private final URI base;

    AuthTestClient(URI base) {
        this.base = base;
    }

    static String devToken() throws IOException {
        try (InputStream is = AuthTestClient.class.getResourceAsStream("/dev-token.txt")) {
            return new String(is.readAllBytes()).strip();
        }
    }

    /** Ein syntaktisch gueltiges, aber gefaelschtes Token (Signatur passt nicht). */
    static String forgedToken() throws IOException {
        String[] parts = devToken().split("\\.");
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"iss\":\"https://blocpress.dev\",\"sub\":\"mallory\",\"exp\":2082754800}".getBytes());
        return parts[0] + "." + payload + "." + parts[2];
    }

    /** Mit dem Dev-Schluessel signiertes Token mit den angegebenen Gruppen (groups-Claim). */
    static String tokenWithGroups(String... groups) {
        return Jwt.issuer(DEV_ISSUER)
                .subject("rita")
                .groups(java.util.Set.of(groups))
                .expiresIn(3600)
                .sign("dev-privatekey.pem");
    }

    /** POST /api/render/templates/import mit einer vollstaendigen Vorlage namens {@code name}. */
    int importTemplate(String name, String token) throws Exception {
        byte[] odt;
        try (InputStream is = AuthTestClient.class.getResourceAsStream("/kuendigung.odt")) {
            odt = is.readAllBytes();
        }
        String body = "{\"id\":\"" + UUID.randomUUID() + "\",\"name\":\"" + name + "\",\"version\":1,"
                + "\"contentBase64\":\"" + Base64.getEncoder().encodeToString(odt) + "\","
                + "\"validFrom\":\"2026-01-01T00:00:00\"}";
        return postJson("/api/render/templates/import", body, token);
    }

    int delete(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder(base.resolve(path)).DELETE(), token);
    }

    int get(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder(base.resolve(path)).GET(), token);
    }

    int postJson(String path, String json, String token) throws Exception {
        return send(HttpRequest.newBuilder(base.resolve(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)), token);
    }

    /** POST /api/render/template mit Base64-Vorlage, Ausgabe ODT. */
    int renderInline(String token) throws Exception {
        byte[] odt;
        try (InputStream is = AuthTestClient.class.getResourceAsStream("/kuendigung.odt")) {
            odt = is.readAllBytes();
        }
        String body = "{\"template\":\"" + Base64.getEncoder().encodeToString(odt)
                + "\",\"data\":{},\"outputType\":\"odt\"}";
        return send(HttpRequest.newBuilder(base.resolve("/api/render/template"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/vnd.oasis.opendocument.text")
                .POST(HttpRequest.BodyPublishers.ofString(body)), token);
    }

    private int send(HttpRequest.Builder request, String token) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
