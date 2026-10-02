package io.github.flaechsig.blocpress.reqcheck;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Erzeugt {@code docs/spec/requirements/CATALOG.md} — die menschenlesbare
 * Lese-Ansicht der Requirements. Die wesentliche Information liegt in den
 * einzelnen Dateien als YAML-Frontmatter (maschinenlesbar); diese Seite rendert
 * sie deterministisch als Fliesstext, sodass man nicht in die Quelle schauen
 * muss. NICHT editieren — wird bei {@code mvn verify} neu erzeugt.
 */
final class CatalogGenerator {

    private CatalogGenerator() {
    }

    static void generate(Path root) {
        Path reqDir = root.resolve("docs/spec/requirements");
        if (!Files.isDirectory(reqDir)) {
            return;
        }

        StringBuilder md = new StringBuilder();
        md.append("<!-- GENERIERT von blocpress-req-check aus den Requirement-Frontmattern.\n");
        md.append("     NICHT EDITIEREN — wird bei `mvn verify` neu erzeugt. -->\n\n");
        md.append("# Requirements-Katalog (Lese-Ansicht)\n\n");
        md.append("Menschenlesbare Fassung von [`docs/spec/requirements/`](.). ");
        md.append("Quelle jeder Anforderung ist die gleichnamige `REQ-NNNN.md`.\n\n");

        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(reqDir)) {
            s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("REQ-") && n.endsWith(".md");
            }).sorted().forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + reqDir + " nicht lesen", e);
        }

        for (Path p : files) {
            Frontmatter fm = Frontmatter.parse(p);
            String id = fm.get("id");
            String file = p.getFileName().toString();

            md.append("## ").append(id);
            if (!fm.get("status").isBlank()) {
                md.append("  ·  `").append(fm.get("status")).append('`');
            }
            md.append("\n\n");

            md.append("> ").append(oneLine(fm.get("statement"))).append("\n\n");

            md.append("| | |\n|---|---|\n");
            row(md, "Obligation", fm.get("obligation"));
            row(md, "Status", fm.get("status"));
            row(md, "Confidence", fm.get("confidence"));
            row(md, "Source", fm.get("source"));
            row(md, "Supersedes", fm.get("supersedes"));
            row(md, "Superseded by", fm.get("superseded_by"));
            row(md, "Derived from", fm.get("derived_from"));
            md.append('\n');

            if (!fm.get("rationale").isBlank()) {
                md.append("**Rationale:** ").append(oneLine(fm.get("rationale"))).append("\n\n");
            }

            List<String> evidence = fm.list("evidence");
            if (!evidence.isEmpty()) {
                md.append("**Evidence:**\n\n");
                for (String ev : evidence) {
                    md.append("- `").append(ev).append("`\n");
                }
                md.append('\n');
            }

            md.append("_Quelle: [`").append(file).append("`](").append(file).append(")_\n\n");
            md.append("---\n\n");
        }

        Path out = reqDir.resolve("CATALOG.md");
        try {
            Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + out + " nicht schreiben", e);
        }
        System.out.println("[req-check] docs/spec/requirements/CATALOG.md generiert.");
    }

    private static void row(StringBuilder md, String label, String value) {
        if (!value.isBlank()) {
            md.append("| **").append(label).append("** | ").append(value).append(" |\n");
        }
    }

    private static String oneLine(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }
}
