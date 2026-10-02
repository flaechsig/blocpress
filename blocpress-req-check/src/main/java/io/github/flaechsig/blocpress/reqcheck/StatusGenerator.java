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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Erzeugt {@code docs/STATUS.md} deterministisch aus den Frontmattern aller
 * Requirements plus dem arc42-Skelett. Kein Datum im Inhalt — nur echte
 * Inhaltsaenderungen erzeugen ein Git-Diff. Ein handgepflegtes
 * Inhaltsverzeichnis ist nach Monaten falsch; deshalb generiert.
 */
final class StatusGenerator {

    /** Anzeige-Reihenfolge der Requirement-Status. */
    private static final List<String> STATUS_ORDER =
            List.of("implemented", "planned", "proposed", "rejected", "superseded");

    private static final Pattern ARC42_HEADING = Pattern.compile("^==\\s+(.+?)\\s*$");
    private static final Pattern ARC42_STATUS = Pattern.compile("^//\\s*arc42-status:\\s*(\\S+)");

    private record Req(String id, String statement, String status, String source, String confidence) {
    }

    private StatusGenerator() {
    }

    static void generate(Path root) {
        List<Req> reqs = loadRequirements(root.resolve("docs/spec/requirements"));
        Map<String, String> arc42 = loadArc42Chapters(root.resolve("docs/architecture/index.adoc"));

        StringBuilder md = new StringBuilder();
        md.append("<!-- GENERIERT von blocpress-req-check aus den Frontmattern + arc42-Skelett.\n");
        md.append("     NICHT EDITIEREN — wird bei `mvn verify` neu erzeugt. -->\n\n");
        md.append("# blocpress — Doku-Status (generiert)\n\n");
        md.append("Erzählerischer Einstieg: **[README](README.md)**. ");
        md.append("Diese Seite ist der generierte Statusspiegel.\n\n");
        md.append("**Weitere Ansichten:** ");
        md.append("[Requirements-Katalog](spec/requirements/CATALOG.md) · ");
        md.append("[Planung (Epics → Stories → Requirements)](planning/README.md)\n\n");

        List<String> arc42Contradictions = loadArc42Contradictions(root.resolve("docs/architecture/index.adoc"));

        appendRequirementStatus(md, reqs);
        appendArc42Status(md, arc42);
        appendContradictions(md, reqs, arc42Contradictions);

        Path out = root.resolve("docs/STATUS.md");
        try {
            Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + out + " nicht schreiben", e);
        }
        System.out.println("[req-check] docs/STATUS.md generiert.");
    }

    private static void appendRequirementStatus(StringBuilder md, List<Req> reqs) {
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (String s : STATUS_ORDER) {
            byStatus.put(s, 0);
        }
        for (Req r : reqs) {
            String status = r.status().isBlank() ? "(ohne Status)" : r.status();
            byStatus.merge(status, 1, Integer::sum);
        }
        md.append("## Status\n\n");
        md.append("### Requirements je Status\n\n");
        md.append("| Status | Anzahl |\n|---|---|\n");
        for (Map.Entry<String, Integer> e : byStatus.entrySet()) {
            md.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
        }
        md.append("| **Gesamt** | **").append(reqs.size()).append("** |\n\n");
    }

    private static void appendArc42Status(StringBuilder md, Map<String, String> arc42) {
        List<String> unknown = new ArrayList<>();
        for (Map.Entry<String, String> e : arc42.entrySet()) {
            if (!"FILLED".equalsIgnoreCase(e.getValue())) {
                unknown.add(e.getKey());
            }
        }
        md.append("### arc42-Kapitel\n\n");
        if (arc42.isEmpty()) {
            md.append("Kein arc42-Skelett gefunden.\n\n");
            return;
        }
        md.append(unknown.size()).append(" von ").append(arc42.size())
          .append(" Kapiteln noch offen (nicht FILLED):\n\n");
        for (String chapter : unknown) {
            md.append("- ").append(chapter).append(" — `").append(arc42.get(chapter)).append("`\n");
        }
        md.append("\n");
    }

    private static void appendContradictions(StringBuilder md, List<Req> reqs, List<String> arc42Contradictions) {
        md.append("## Widersprüche (confidence: contradicted)\n\n");
        List<Req> contradicted = reqs.stream()
                .filter(r -> "contradicted".equalsIgnoreCase(r.confidence()))
                .toList();
        if (contradicted.isEmpty() && arc42Contradictions.isEmpty()) {
            md.append("Keine.\n\n");
            return;
        }
        for (Req r : contradicted) {
            md.append("- **").append(r.id()).append("** — ").append(r.statement());
            if (!r.source().isBlank()) {
                md.append(" _(Quelle: ").append(r.source()).append(")_");
            }
            md.append("\n");
        }
        for (String c : arc42Contradictions) {
            md.append("- **arc42** — ").append(c).append("\n");
        }
        md.append("\n");
    }

    /** Sammelt '// arc42-contradiction: <text>'-Marker aus dem arc42-Gerüst. */
    private static List<String> loadArc42Contradictions(Path indexAdoc) {
        List<String> out = new ArrayList<>();
        if (!Files.isRegularFile(indexAdoc)) {
            return out;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(indexAdoc, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + indexAdoc + " nicht lesen", e);
        }
        String marker = "// arc42-contradiction:";
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(marker)) {
                out.add(trimmed.substring(marker.length()).trim());
            }
        }
        return out;
    }

    private static List<Req> loadRequirements(Path reqDir) {
        List<Req> reqs = new ArrayList<>();
        if (!Files.isDirectory(reqDir)) {
            return reqs;
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
                     reqs.add(new Req(
                             id.isBlank() ? p.getFileName().toString() : id,
                             fm.get("statement"),
                             fm.get("status").toLowerCase(),
                             fm.get("source"),
                             fm.get("confidence")));
                 });
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + reqDir + " nicht lesen", e);
        }
        return reqs;
    }

    /** Kapiteltitel -> arc42-status, in Reihenfolge des Dokuments. */
    private static Map<String, String> loadArc42Chapters(Path indexAdoc) {
        Map<String, String> chapters = new LinkedHashMap<>();
        if (!Files.isRegularFile(indexAdoc)) {
            return chapters;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(indexAdoc, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + indexAdoc + " nicht lesen", e);
        }
        String current = null;
        for (String line : lines) {
            Matcher h = ARC42_HEADING.matcher(line);
            if (h.matches()) {
                current = h.group(1).trim();
                chapters.putIfAbsent(current, "UNKNOWN");
                continue;
            }
            Matcher s = ARC42_STATUS.matcher(line.trim());
            if (s.find() && current != null) {
                chapters.put(current, s.group(1).trim());
            }
        }
        return chapters;
    }
}
