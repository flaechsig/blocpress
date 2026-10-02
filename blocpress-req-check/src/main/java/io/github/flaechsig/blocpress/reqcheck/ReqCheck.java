package io.github.flaechsig.blocpress.reqcheck;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Traceability-Gate. Reiner Build-Schritt, kein LLM: ob eine Anforderung
 * implementiert ist, ist eine Tatsachenfrage — sie wird an den gruenen Tests
 * abgelesen, nicht entschieden.
 *
 * <p>Vergleicht die von {@code blocpress-req-trace} pro Modul geschriebenen
 * {@code target/req-coverage.json} gegen die Frontmatter der Dateien unter
 * {@code docs/spec/requirements/} und bricht bei fuenf Fehlerklassen ab:
 * <ol>
 *   <li>{@code status: implemented}, aber kein gruener Test zur REQ-ID</li>
 *   <li>Ein Test traegt eine REQ-ID, die im Katalog fehlt</li>
 *   <li>{@code status: planned}, aber Tests dazu sind gruen</li>
 *   <li>Eine Story unter {@code docs/spec/stories/} verweist auf ein unbekanntes Requirement</li>
 *   <li>Eine Story verweist auf einen unbekannten Epic</li>
 * </ol>
 *
 * <p>Erzeugt im selben Lauf die generierten Lese-Ansichten
 * {@code docs/STATUS.md}, {@code docs/spec/requirements/CATALOG.md} und
 * {@code docs/planning/README.md}.
 *
 * <p>Abschaltbar ueber {@code -Dreq.check.skip=true}.
 *
 * <p>Bewusst ohne externe Bibliotheken: Frontmatter und das selbst erzeugte
 * Coverage-JSON werden von Hand geparst. Das Format liegt vollstaendig in
 * eigener Hand — nichts Haltbares haengt an diesem Werkzeug.
 */
public final class ReqCheck {

    private static final Pattern COVERAGE_ENTRY = Pattern.compile(
            "\\{\\s*\"req\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"result\"\\s*:\\s*\"([^\"]+)\"\\s*}");

    private ReqCheck() {
    }

    public static void main(String[] args) {
        Path root = resolveRoot();
        System.out.println("[req-check] Reactor-Wurzel: " + root);

        // Generierte Ansichten immer neu erzeugen — auch bei uebersprungenem
        // Gate, damit die Uebersichten den aktuellen Stand widerspiegeln.
        StatusGenerator.generate(root);
        CatalogGenerator.generate(root);
        PlanningGenerator.generate(root);
        SpecGenerator.generate(root);

        if (Boolean.parseBoolean(System.getProperty("req.check.skip", "false"))) {
            System.out.println("[req-check] Gate uebersprungen (-Dreq.check.skip=true)");
            return;
        }

        Path reqDir = root.resolve("docs/spec/requirements");

        Map<String, String> catalog = loadCatalog(reqDir);      // REQ-ID -> status
        Map<String, Outcome> coverage = loadCoverage(root);      // REQ-ID -> aggregiertes Ergebnis
        List<PlanningReader.Story> stories = PlanningReader.readStories(root);
        List<PlanningReader.Epic> epics = PlanningReader.readEpics(root);
        java.util.Set<String> epicIds = new java.util.LinkedHashSet<>();
        for (PlanningReader.Epic e : epics) {
            epicIds.add(e.id());
        }

        List<String> errors = new ArrayList<>();

        // Fehlerklasse 1: implemented ohne gruenen Test.
        for (Map.Entry<String, String> e : catalog.entrySet()) {
            if ("implemented".equals(e.getValue()) && coverage.get(e.getKey()) != Outcome.PASSED) {
                Outcome actual = coverage.get(e.getKey());
                errors.add("[1] " + e.getKey() + ": status=implemented, aber kein gruener Test ("
                        + (actual == null ? "kein getaggter Test gefunden" : "Ergebnis=" + actual) + ")");
            }
        }

        // Fehlerklasse 2: getaggter Test ohne Katalogeintrag.
        for (String req : coverage.keySet()) {
            if (!catalog.containsKey(req)) {
                errors.add("[2] " + req + ": Test getaggt, aber im Katalog docs/spec/requirements/ nicht vorhanden");
            }
        }

        // Fehlerklasse 3: planned, aber Tests gruen.
        for (Map.Entry<String, String> e : catalog.entrySet()) {
            if ("planned".equals(e.getValue()) && coverage.get(e.getKey()) == Outcome.PASSED) {
                errors.add("[3] " + e.getKey() + ": status=planned, aber Tests dazu sind gruen — Status pflegen");
            }
        }

        // Fehlerklasse 4/5: Planungsschicht verweist ins Leere.
        for (PlanningReader.Story s : stories) {
            for (String req : s.requirements()) {
                if (!catalog.containsKey(req)) {
                    errors.add("[4] " + s.id() + ": verweist auf unbekanntes Requirement '" + req + "'");
                }
            }
            if (!s.epic().isBlank() && !epicIds.contains(s.epic())) {
                errors.add("[5] " + s.id() + ": verweist auf unbekannten Epic '" + s.epic() + "'");
            }
        }

        // Fehlerklasse 6/7/8: Story-/Epic-`status` (Lebenszyklus) gepflegt + belegpflichtig
        // (siehe CONVENTIONS.md „Story-/Epic-status ist gepflegt, aber belegpflichtig").
        java.util.Set<String> allowedStatus = java.util.Set.of(
                "open", "in-progress", "verified", "superseded", "retired");
        for (PlanningReader.Story s : stories) {
            String st = s.status();
            if (st.isBlank() || !allowedStatus.contains(st)) {
                errors.add("[6] " + s.id() + ": ungueltiger/fehlender status '" + st
                        + "' (erlaubt: open|in-progress|verified|superseded|retired)");
                continue;
            }
            if ("verified".equals(st) && s.requirements().isEmpty() && s.evidence().isEmpty()) {
                errors.add("[7] " + s.id()
                        + ": status=verified, aber weder requirements noch evidence (Belegpflicht)");
            }
            if (("superseded".equals(st) || "retired".equals(st)) && s.supersededBy().isBlank()) {
                errors.add("[8] " + s.id() + ": status=" + st + ", aber kein superseded_by (ADR-Pointer)");
            }
        }
        for (PlanningReader.Epic e : epics) {
            String st = e.status();
            if (st.isBlank() || !allowedStatus.contains(st)) {
                errors.add("[6] " + e.id() + ": ungueltiger/fehlender status '" + st + "'");
            } else if (("superseded".equals(st) || "retired".equals(st)) && e.supersededBy().isBlank()) {
                errors.add("[8] " + e.id() + ": status=" + st + ", aber kein superseded_by (ADR-Pointer)");
            }
        }

        System.out.printf("[req-check] Katalog: %d Requirements, Coverage: %d getaggte REQ-IDs, "
                + "Planung: %d Epics / %d Stories%n",
                catalog.size(), coverage.size(), epicIds.size(), stories.size());

        if (errors.isEmpty()) {
            System.out.println("[req-check] OK — Katalog und Testabdeckung konsistent.");
            return;
        }

        System.err.println("[req-check] FEHLGESCHLAGEN — " + errors.size() + " Abweichung(en):");
        errors.forEach(err -> System.err.println("  - " + err));
        System.exit(1);
    }

    private enum Outcome { SKIPPED, PASSED, FAILED }

    private static Path resolveRoot() {
        String explicit = System.getProperty("req.check.root");
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getProperty("maven.multiModuleProjectDirectory");
        }
        if (explicit != null && !explicit.isBlank()) {
            return Paths.get(explicit).toAbsolutePath().normalize();
        }
        // Fallback: dieses Modul liegt eine Ebene unter der Reactor-Wurzel.
        return Paths.get("").toAbsolutePath().getParent();
    }

    private static Map<String, String> loadCatalog(Path reqDir) {
        Map<String, String> catalog = new TreeMap<>();
        if (!Files.isDirectory(reqDir)) {
            System.out.println("[req-check] Hinweis: " + reqDir + " existiert nicht — leerer Katalog.");
            return catalog;
        }
        try (Stream<Path> files = Files.list(reqDir)) {
            files.filter(p -> {
                     String n = p.getFileName().toString();
                     return n.startsWith("REQ-") && n.endsWith(".md");
                 })
                 .sorted()
                 .forEach(p -> {
                     Frontmatter fm = Frontmatter.parse(p);
                     String id = fm.get("id");
                     String status = fm.get("status").toLowerCase();
                     if (id.isBlank()) {
                         throw new IllegalStateException("Kein 'id' im Frontmatter: " + p);
                     }
                     if (catalog.containsKey(id)) {
                         throw new IllegalStateException("Doppelte REQ-ID '" + id + "' (" + p + ")");
                     }
                     catalog.put(id, status);
                 });
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + reqDir + " nicht lesen", e);
        }
        return catalog;
    }

    private static Map<String, Outcome> loadCoverage(Path root) {
        Map<String, Outcome> coverage = new TreeMap<>();
        try (Stream<Path> children = Files.list(root)) {
            children.filter(Files::isDirectory)
                    .map(dir -> dir.resolve("target/req-coverage.json"))
                    .filter(Files::isRegularFile)
                    .forEach(file -> mergeCoverageFile(file, coverage));
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte Module unter " + root + " nicht auflisten", e);
        }
        return coverage;
    }

    private static void mergeCoverageFile(Path file, Map<String, Outcome> coverage) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + file + " nicht lesen", e);
        }
        Matcher m = COVERAGE_ENTRY.matcher(content);
        while (m.find()) {
            String req = m.group(1);
            Outcome outcome = Outcome.valueOf(m.group(2));
            coverage.merge(req, outcome, (a, b) -> a.ordinal() >= b.ordinal() ? a : b);
        }
    }
}
