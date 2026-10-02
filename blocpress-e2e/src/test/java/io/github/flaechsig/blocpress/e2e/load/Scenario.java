package io.github.flaechsig.blocpress.e2e.load;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Ein Render-Fall des Lastmix: Vorlage + Daten + Soll-Inhalte ({@code load/scenarios.json}).
 *
 * <p>Jeder Render wird inhaltlich geprueft, nicht nur der HTTP-Status: PDF vorhanden,
 * Pflicht-Texte enthalten (deutsches Zahlenformat!), verbotene Texte nicht enthalten und
 * Wortfolge identisch zur Referenz aus dem sequenziellen Vorlauf.</p>
 */
record Scenario(String name, String covers, byte[] template, JsonNode data,
                List<String> expect, List<String> forbid) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static List<Scenario> load() throws IOException {
        JsonNode root;
        try (InputStream is = Scenario.class.getResourceAsStream("/load/scenarios.json")) {
            root = MAPPER.readTree(is);
        }
        List<Scenario> result = new ArrayList<>();
        for (JsonNode s : root.get("scenarios")) {
            result.add(new Scenario(
                    s.get("name").asText(),
                    s.path("covers").asText(""),
                    Files.readAllBytes(Path.of(s.get("template").asText())),
                    s.get("data"),
                    texts(s.path("expect")),
                    texts(s.path("forbid"))));
        }
        return result;
    }

    /** Request-Body fuer {@code POST /api/render/template} (JSON, Vorlage Base64). */
    String renderRequestJson() {
        var body = MAPPER.createObjectNode();
        body.put("template", Base64.getEncoder().encodeToString(template));
        body.set("data", data);
        body.put("outputType", "pdf");
        return body.toString();
    }

    /** Name, unter dem die Vorlage fuer den Job-Pfad importiert wird. */
    String templateName() {
        return "loadtest-" + name;
    }

    /**
     * @return {@code null}, wenn der Inhalt stimmt — sonst eine kurze Fehlerbeschreibung
     */
    String check(byte[] pdf, String reference) {
        if (pdf == null || pdf.length == 0) {
            return "leeres Dokument";
        }
        if (pdf.length < 5 || !new String(pdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            return "kein PDF";
        }
        String text;
        try {
            text = words(pdf);
        } catch (IOException e) {
            return "PDF nicht lesbar: " + e.getMessage();
        }
        for (String e : expect) {
            if (!text.contains(normalize(e))) {
                return "fehlt: '" + e + "'";
            }
        }
        for (String f : forbid) {
            if (text.contains(normalize(f))) {
                return "unerwartet: '" + f + "'";
            }
        }
        if (reference != null && !reference.equals(text)) {
            return "weicht von Referenz ab";
        }
        return null;
    }

    /** Text des PDFs mit normalisiertem Whitespace (Zeilenumbruch haengt von Schriften ab). */
    static String words(byte[] pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return normalize(new PDFTextStripper().getText(doc));
        }
    }

    private static String normalize(String s) {
        return s.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }
}
