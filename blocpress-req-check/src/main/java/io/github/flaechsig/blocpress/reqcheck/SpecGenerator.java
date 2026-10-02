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
import java.util.stream.Stream;

/**
 * Erzeugt {@code docs/spec/SPEC.md} — das Gesamtdokument der Spec: Vision-Verweis,
 * Status-Legende und -Überblick, dann je Epic sein {@code status} + Rumpf und
 * darunter <b>jede zugehörige User Story inline</b> (nicht nur verlinkt), inkl.
 * der requirement-losen Backlog-Stories. Der Status kommt aus dem gepflegten
 * {@code status}-Frontmatter (Lebenszyklus-Achse, siehe CONVENTIONS.md), die
 * Badges bilden ihn optisch ab.
 *
 * <p>Bewusst eigenes Datei-Lesen (Frontmatter via {@link Frontmatter}, Body separat),
 * damit die bestehenden Reader/Generatoren unberührt bleiben.
 */
final class SpecGenerator {

    private SpecGenerator() {
    }

    /** Reihenfolge der Status im Überblick + Badges. */
    private static final Map<String, String> BADGE = new LinkedHashMap<>();
    static {
        BADGE.put("verified", "✅");
        BADGE.put("in-progress", "🟡");
        BADGE.put("open", "⚪");
        BADGE.put("superseded", "⛔");
        BADGE.put("retired", "⚰️");
    }

    private record Item(String id, String title, String epic, String status,
                        List<String> requirements, List<String> evidence,
                        String supersededBy, String derivedFrom, String body) {
    }

    static void generate(Path root) {
        List<Item> epics = read(root.resolve("docs/spec/epics"));
        List<Item> stories = read(root.resolve("docs/spec/stories"));
        if (epics.isEmpty() && stories.isEmpty()) {
            return;
        }

        StringBuilder md = new StringBuilder();
        md.append("<!-- GENERIERT von blocpress-req-check aus docs/spec/VISION.md,\n")
          .append("     docs/spec/epics/ und docs/spec/stories/.\n")
          .append("     NICHT EDITIEREN — wird bei `mvn verify` neu erzeugt. -->\n\n");
        md.append("# blocpress — Spec (Gesamtdokument, generiert)\n\n");
        md.append("Die vollständige Beschreibung in einem Dokument: [Vision](VISION.md) → ")
          .append("Epics → User Stories (inline). Der `status` jeder Story/jedes Epics ist ")
          .append("gepflegt und **belegpflichtig** (Regeln: [CONVENTIONS](../CONVENTIONS.md)).\n\n");

        md.append("## Status-Legende\n\n");
        md.append("| Badge | `status` | Bedeutung |\n|---|---|---|\n");
        md.append("| ✅ | `verified` | vollständig umgesetzt **und** gegen Code bestätigt |\n");
        md.append("| 🟡 | `in-progress` | Kern gebaut, Klausel(n) offen |\n");
        md.append("| ⚪ | `open` | Kandidat, nicht committed |\n");
        md.append("| ⛔ | `superseded` | durch Entscheidung abgelöst (→ ADR) |\n");
        md.append("| ⚰️ | `retired` | war umgesetzt, entfernt/deprecated |\n\n");

        md.append("## Überblick\n\n");
        md.append("| Status | Stories |\n|---|---|\n");
        for (String st : BADGE.keySet()) {
            long n = stories.stream().filter(s -> st.equals(s.status())).count();
            if (n > 0) {
                md.append("| ").append(BADGE.get(st)).append(' ').append(st)
                  .append(" | ").append(n).append(" |\n");
            }
        }
        md.append("| **Summe** | **").append(stories.size()).append("** |\n\n");

        for (Item e : epics) {
            md.append("## ").append(e.id()).append(" — ").append(e.title())
              .append("  ").append(badge(e.status())).append('\n');
            appendBody(md, e.body(), e.id());
            List<Item> own = stories.stream().filter(s -> e.id().equals(s.epic())).toList();
            for (Item s : own) {
                appendStory(md, s);
            }
        }

        List<Item> orphans = stories.stream()
                .filter(s -> epics.stream().noneMatch(e -> e.id().equals(s.epic()))).toList();
        if (!orphans.isEmpty()) {
            md.append("## Ohne gültigen Epic\n\n");
            for (Item s : orphans) {
                appendStory(md, s);
            }
        }

        write(root.resolve("docs/spec/SPEC.md"), md.toString());
        System.out.println("[req-check] docs/spec/SPEC.md generiert.");
    }

    private static void appendStory(StringBuilder md, Item s) {
        md.append("### ").append(s.id()).append(" — ").append(s.title())
          .append("  ").append(badge(s.status())).append('\n');
        appendBody(md, s.body(), s.id());

        List<String> meta = new ArrayList<>();
        if (!s.requirements().isEmpty()) {
            meta.add("**Requirements:** " + String.join(", ", s.requirements()));
        }
        if (!s.evidence().isEmpty()) {
            meta.add("**Evidence:** " + String.join(", ", s.evidence()));
        }
        if (!s.supersededBy().isBlank()) {
            meta.add("**Abgelöst durch:** " + s.supersededBy());
        }
        if (!s.derivedFrom().isBlank()) {
            meta.add("**Herkunft:** " + s.derivedFrom());
        }
        if (!meta.isEmpty()) {
            md.append(String.join(" · ", meta)).append('\n');
        }
        md.append('\n');
    }

    private static void appendBody(StringBuilder md, String body, String id) {
        String b = body == null ? "" : body.strip();
        // Führende, die generierte Überschrift duplizierende Titel-Zeile entfernen
        // (Rumpf beginnt mit "# ...<id>..."). Andere Überschriften (z.B. "## Ziel") bleiben.
        if (b.startsWith("#")) {
            int nl = b.indexOf('\n');
            String firstLine = nl < 0 ? b : b.substring(0, nl);
            if (firstLine.contains(id)) {
                b = nl < 0 ? "" : b.substring(nl + 1).strip();
            }
        }
        md.append('\n');
        if (!b.isEmpty()) {
            md.append(b).append("\n\n");
        }
    }

    private static String badge(String status) {
        return BADGE.getOrDefault(status, "❔") + " `" + (status.isBlank() ? "?" : status) + "`";
    }

    private static List<Item> read(Path dir) {
        List<Item> items = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return items;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".md"))
                 .sorted()
                 .forEach(p -> {
                     Frontmatter fm = Frontmatter.parse(p);
                     String id = fm.get("id");
                     if (id.isBlank()) {
                         return;
                     }
                     items.add(new Item(id, fm.get("title"), fm.get("epic"), fm.get("status"),
                             fm.list("requirements"), fm.list("evidence"),
                             fm.get("superseded_by"), fm.get("derived_from"), body(p)));
                 });
        } catch (IOException ex) {
            throw new UncheckedIOException("Konnte " + dir + " nicht lesen", ex);
        }
        items.sort((a, b) -> a.id().compareTo(b.id()));
        return items;
    }

    /** Rumpf hinter dem zweiten {@code ---} (das eigentliche Story-/Epic-Markdown). */
    private static String body(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + file + " nicht lesen", e);
        }
        int seen = 0;
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (seen < 2) {
                if (line.trim().equals("---")) {
                    seen++;
                }
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + file + " nicht schreiben", e);
        }
    }
}
