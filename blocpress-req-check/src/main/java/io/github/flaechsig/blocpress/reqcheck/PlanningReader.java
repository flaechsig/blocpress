package io.github.flaechsig.blocpress.reqcheck;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Liest die Planungsschicht unter {@code docs/planning/} — Epics und User
 * Stories als Markdown mit Frontmatter. Eine Story verweist ueber
 * {@code requirements: [REQ-...]} auf den Requirements-Katalog und ueber
 * {@code epic: E-...} auf ihren Epic; genau diese Verweise prueft das Gate.
 */
final class PlanningReader {

    record Epic(String id, String title, String status, String supersededBy) {
    }

    record Story(String id, String title, String epic, List<String> requirements,
                 String status, List<String> evidence, String supersededBy) {
    }

    private PlanningReader() {
    }

    static List<Epic> readEpics(Path root) {
        List<Epic> epics = new ArrayList<>();
        forEachMarkdown(root.resolve("docs/spec/epics"), fm -> {
            String id = fm.get("id");
            if (!id.isBlank()) {
                epics.add(new Epic(id, fm.get("title"), fm.get("status"), fm.get("superseded_by")));
            }
        });
        epics.sort((a, b) -> a.id().compareTo(b.id()));
        return epics;
    }

    static Set<String> readEpicIds(Path root) {
        Set<String> ids = new LinkedHashSet<>();
        for (Epic e : readEpics(root)) {
            ids.add(e.id());
        }
        return ids;
    }

    static List<Story> readStories(Path root) {
        List<Story> stories = new ArrayList<>();
        forEachMarkdown(root.resolve("docs/spec/stories"), fm -> {
            String id = fm.get("id");
            if (!id.isBlank()) {
                stories.add(new Story(id, fm.get("title"), fm.get("epic"),
                        fm.list("requirements"), fm.get("status"),
                        fm.list("evidence"), fm.get("superseded_by")));
            }
        });
        stories.sort((a, b) -> a.id().compareTo(b.id()));
        return stories;
    }

    private static void forEachMarkdown(Path dir, java.util.function.Consumer<Frontmatter> consumer) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".md"))
                 .sorted()
                 .forEach(p -> consumer.accept(Frontmatter.parse(p)));
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + dir + " nicht lesen", e);
        }
    }
}
