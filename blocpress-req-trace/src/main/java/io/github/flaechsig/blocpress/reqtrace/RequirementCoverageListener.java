package io.github.flaechsig.blocpress.reqtrace;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestTag;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * JUnit-Platform-Listener, der pro Testlauf festhaelt, welche Requirement-IDs
 * (Tests mit {@code @Tag("REQ-NNNN")}) gruen, rot oder uebersprungen sind, und
 * das Ergebnis nach {@code target/req-coverage.json} des jeweiligen Moduls
 * schreibt.
 *
 * <p>Registriert wird der Listener ueber
 * {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}.
 * Er laeuft im aeusseren Launcher-Classloader und ist damit unabhaengig vom
 * isolierten {@code @QuarkusTest}-Classloader — die Tags haengen an der bei der
 * Discovery erzeugten {@link TestIdentifier} und sind hier sichtbar.
 *
 * <p>Bewusst ohne externe Bibliotheken: das JSON wird von Hand erzeugt, das
 * Format ist die einzige Quelle fuer {@code blocpress-req-check}. Nichts
 * Haltbares haengt an diesem Werkzeug.
 */
public class RequirementCoverageListener implements TestExecutionListener {

    /** Erlaubte Form einer Requirement-ID, z.B. REQ-0042. */
    private static final Pattern REQ_TAG = Pattern.compile("REQ-\\d{4,}");

    /** Ergebnis je Requirement — hoehere Ordinalzahl "gewinnt" bei Aggregation. */
    private enum Outcome { SKIPPED, PASSED, FAILED }

    /** REQ-ID -> aggregiertes Ergebnis ueber alle zugehoerigen Tests. */
    private final Map<String, Outcome> results = new TreeMap<>();

    @Override
    public void executionSkipped(TestIdentifier identifier, String reason) {
        for (String req : requirementTags(identifier)) {
            merge(req, Outcome.SKIPPED);
        }
    }

    @Override
    public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
        if (!identifier.isTest()) {
            return;
        }
        Outcome outcome = switch (result.getStatus()) {
            case SUCCESSFUL -> Outcome.PASSED;
            case ABORTED -> Outcome.SKIPPED;
            case FAILED -> Outcome.FAILED;
        };
        for (String req : requirementTags(identifier)) {
            merge(req, outcome);
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        writeReport();
    }

    private static Iterable<String> requirementTags(TestIdentifier identifier) {
        return identifier.getTags().stream()
                .map(TestTag::getName)
                .filter(name -> REQ_TAG.matcher(name).matches())
                .toList();
    }

    private void merge(String req, Outcome outcome) {
        results.merge(req, outcome, (a, b) -> a.ordinal() >= b.ordinal() ? a : b);
    }

    private void writeReport() {
        String module = System.getProperty("req.trace.module",
                Paths.get("").toAbsolutePath().getFileName().toString());
        Path out = Paths.get(System.getProperty("req.trace.output", "target/req-coverage.json"));

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"module\": \"").append(escape(module)).append("\",\n");
        json.append("  \"entries\": [");
        boolean first = true;
        for (Map.Entry<String, Outcome> e : results.entrySet()) {
            json.append(first ? "\n" : ",\n");
            first = false;
            json.append("    {\"req\": \"").append(escape(e.getKey()))
                .append("\", \"result\": \"").append(e.getValue().name()).append("\"}");
        }
        json.append(results.isEmpty() ? "" : "\n  ");
        json.append("]\n}\n");

        try {
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            Files.writeString(out, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Konnte req-coverage.json nicht schreiben: " + out, ex);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
