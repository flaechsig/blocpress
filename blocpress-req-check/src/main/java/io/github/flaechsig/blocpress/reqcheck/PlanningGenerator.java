package io.github.flaechsig.blocpress.reqcheck;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Erzeugt {@code docs/planning/README.md} — die Übersicht der Planungsschicht:
 * Epics → User Stories → Requirements, plus Rückwärtsindex (Requirement →
 * Stories) und die Liste verwaister Requirements (ohne Story).
 *
 * <p>Status wird <b>abgeleitet, nicht gepflegt</b>: der Status einer Story folgt
 * aus dem Status ihrer Requirements, der eines Epics aus dem seiner Stories. So
 * kann kein handgesetzter Status von der Realität abweichen. NICHT editieren —
 * wird bei {@code mvn verify} neu erzeugt.
 */
final class PlanningGenerator {

    private PlanningGenerator() {
    }

    static void generate(Path root) {
        List<PlanningReader.Epic> epics = PlanningReader.readEpics(root);
        List<PlanningReader.Story> stories = PlanningReader.readStories(root);
        if (epics.isEmpty() && stories.isEmpty()) {
            return;
        }
        Map<String, String> reqStatus = requirementStatuses(root.resolve("docs/spec/requirements"));

        StringBuilder md = new StringBuilder();
        md.append("<!-- GENERIERT von blocpress-req-check aus docs/planning/ + den Requirements.\n");
        md.append("     NICHT EDITIEREN — wird bei `mvn verify` neu erzeugt. -->\n\n");
        md.append("# Planung — Epics → User Stories → Requirements\n\n");
        md.append("Bricht die [Vision](../spec/VISION.md) über Epics → Stories → ");
        md.append("[Requirements](../spec/requirements/CATALOG.md) herunter und zeigt den Stand.\n\n");
        md.append("> **Status ist abgeleitet, nicht gepflegt:** eine Story ist `umgesetzt`, ");
        md.append("wenn alle ihre Requirements `implemented` sind; ein Epic ist `umgesetzt`, ");
        md.append("wenn alle seine Stories es sind (sonst `in Arbeit` / `geplant`). ");
        md.append("Der Status bezieht sich auf die hier modellierten Stories. Die ");
        md.append("gewachsene Doku (`product-backlog.adoc`) wird kontrolliert überführt, ");
        md.append("siehe [ROADMAP](ROADMAP.md).\n\n");

        // Vorwärts: je Epic seine Stories und deren Requirements.
        for (PlanningReader.Epic e : epics) {
            List<PlanningReader.Story> own = stories.stream()
                    .filter(s -> e.id().equals(s.epic())).toList();
            md.append("## ").append(e.id()).append(" — ").append(e.title())
              .append("  ·  `").append(epicStatus(own, reqStatus)).append("`\n\n");
            if (own.isEmpty()) {
                md.append("_(noch keine Stories)_\n\n");
                continue;
            }
            for (PlanningReader.Story s : own) {
                appendStoryLine(md, s, reqStatus);
            }
            md.append('\n');
        }

        // Stories mit unbekanntem/leerem Epic (das Gate meldet das separat).
        List<PlanningReader.Story> orphanStories = stories.stream()
                .filter(s -> epics.stream().noneMatch(e -> e.id().equals(s.epic()))).toList();
        if (!orphanStories.isEmpty()) {
            md.append("## Ohne gültigen Epic\n\n");
            for (PlanningReader.Story s : orphanStories) {
                appendStoryLine(md, s, reqStatus);
            }
            md.append('\n');
        }

        // Rückwärts: Requirement -> Stories.
        md.append("## Rückwärtsindex: Requirement → Stories\n\n");
        TreeMap<String, List<String>> byReq = new TreeMap<>();
        for (PlanningReader.Story s : stories) {
            for (String req : s.requirements()) {
                byReq.computeIfAbsent(req, k -> new ArrayList<>()).add(s.id());
            }
        }
        md.append("| Requirement | Stories |\n|---|---|\n");
        for (var entry : byReq.entrySet()) {
            md.append("| ").append(reqLink(entry.getKey())).append(" | ")
              .append(String.join(", ", entry.getValue())).append(" |\n");
        }
        md.append('\n');

        // Verwaiste Requirements (kein Story-Bezug).
        List<String> orphanReqs = new ArrayList<>();
        for (String id : reqStatus.keySet()) {
            if (!byReq.containsKey(id)) {
                orphanReqs.add(id);
            }
        }
        md.append("## Requirements ohne Story\n\n");
        if (orphanReqs.isEmpty()) {
            md.append("Keine — jedes Requirement ist mindestens einer Story zugeordnet.\n");
        } else {
            for (String id : orphanReqs) {
                md.append("- ").append(reqLink(id)).append('\n');
            }
        }
        md.append('\n');

        Path out = root.resolve("docs/planning/README.md");
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + out + " nicht schreiben", e);
        }
        System.out.println("[req-check] docs/planning/README.md generiert.");
    }

    private static void appendStoryLine(StringBuilder md, PlanningReader.Story s,
                                        Map<String, String> reqStatus) {
        md.append("- **").append(s.id()).append("** ").append(s.title())
          .append("  `").append(storyStatus(s, reqStatus)).append('`');
        if (!s.requirements().isEmpty()) {
            List<String> links = new ArrayList<>();
            for (String req : s.requirements()) {
                links.add(reqLink(req));
            }
            md.append(" → ").append(String.join(", ", links));
        }
        md.append('\n');
    }

    /** Story-Status aus dem Status ihrer Requirements ableiten. */
    private static String storyStatus(PlanningReader.Story s, Map<String, String> reqStatus) {
        if (s.requirements().isEmpty()) {
            return "ohne Requirements";
        }
        boolean allImplemented = true;
        boolean anyImplemented = false;
        for (String req : s.requirements()) {
            if ("implemented".equals(reqStatus.get(req))) {
                anyImplemented = true;
            } else {
                allImplemented = false;
            }
        }
        return allImplemented ? "umgesetzt" : (anyImplemented ? "in Arbeit" : "geplant");
    }

    /** Epic-Status aus dem abgeleiteten Status seiner Stories ableiten. */
    private static String epicStatus(List<PlanningReader.Story> own, Map<String, String> reqStatus) {
        if (own.isEmpty()) {
            return "leer";
        }
        int umgesetzt = 0;
        for (PlanningReader.Story s : own) {
            if ("umgesetzt".equals(storyStatus(s, reqStatus))) {
                umgesetzt++;
            }
        }
        if (umgesetzt == own.size()) {
            return "umgesetzt";
        }
        return umgesetzt == 0 ? "geplant" : "in Arbeit";
    }

    private static String reqLink(String reqId) {
        return "[" + reqId + "](../spec/requirements/" + reqId + ".md)";
    }

    private static Map<String, String> requirementStatuses(Path reqDir) {
        Map<String, String> byId = new LinkedHashMap<>();
        if (!Files.isDirectory(reqDir)) {
            return byId;
        }
        try (Stream<Path> s = Files.list(reqDir)) {
            s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("REQ-") && n.endsWith(".md");
            }).sorted().forEach(p -> {
                Frontmatter fm = Frontmatter.parse(p);
                String id = fm.get("id");
                if (!id.isBlank()) {
                    byId.put(id, fm.get("status").toLowerCase());
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + reqDir + " nicht lesen", e);
        }
        return byId;
    }
}
