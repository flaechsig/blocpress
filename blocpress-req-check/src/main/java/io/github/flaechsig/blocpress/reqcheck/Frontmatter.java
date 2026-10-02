package io.github.flaechsig.blocpress.reqcheck;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Minimaler Leser fuer das YAML-Frontmatter der Dokumente (Requirements, Epics,
 * Stories). Bewusst ohne YAML-Bibliothek — das Format liegt in eigener Hand.
 * Unterstuetzt genau die verwendeten Formen zwischen den ersten beiden
 * {@code ---}:
 * <ul>
 *   <li>Skalar: {@code key: value} (interne Doppelpunkte im Wert bleiben)</li>
 *   <li>Block-Skalar: {@code key: >-} bzw. {@code |} mit eingerueckten Folgezeilen</li>
 *   <li>Inline-Liste: {@code key: [a, b]}</li>
 *   <li>Block-Liste: {@code key:} gefolgt von {@code   - item}-Zeilen</li>
 * </ul>
 */
final class Frontmatter {

    private final Map<String, String> scalars;
    private final Map<String, List<String>> lists;

    private Frontmatter(Map<String, String> scalars, Map<String, List<String>> lists) {
        this.scalars = scalars;
        this.lists = lists;
    }

    /** Skalarwert oder "" wenn nicht vorhanden. */
    String get(String key) {
        return scalars.getOrDefault(key, "");
    }

    /** Listenwert (inline oder block) oder leere Liste. */
    List<String> list(String key) {
        return lists.getOrDefault(key, List.of());
    }

    static Frontmatter parse(Path file) {
        Map<String, String> scalars = new TreeMap<>();
        Map<String, List<String>> lists = new TreeMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Konnte " + file + " nicht lesen", e);
        }
        if (lines.isEmpty() || !lines.get(0).trim().equals("---")) {
            throw new IllegalStateException("Kein YAML-Frontmatter (--- am Dateianfang): " + file);
        }

        String key = null;          // aktueller Schluessel mit ausstehendem Block/Liste
        boolean blockScalar = false; // key: >- oder |
        boolean literal = false;     // | statt >
        List<String> blockLines = new ArrayList<>();
        List<String> listItems = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().equals("---")) {
                flush(scalars, lists, key, blockScalar, literal, blockLines, listItems);
                return new Frontmatter(scalars, lists);
            }

            boolean indented = !line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t');
            String trimmed = line.trim();

            if (indented || trimmed.startsWith("- ")) {
                // Fortsetzung des aktuellen Schluessels
                if (key == null || trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("- ")) {
                    listItems.add(unquote(trimmed.substring(2).trim()));
                } else if (blockScalar) {
                    blockLines.add(trimmed);
                }
                continue;
            }

            // Neue Top-Level-Zeile -> vorherigen Schluessel abschliessen
            flush(scalars, lists, key, blockScalar, literal, blockLines, listItems);
            key = null;
            blockScalar = false;
            literal = false;
            blockLines = new ArrayList<>();
            listItems = new ArrayList<>();

            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String k = line.substring(0, colon).trim();
            String rest = line.substring(colon + 1).trim();

            if (rest.isEmpty()) {
                key = k; // Block-Liste oder leerer Wert folgt
            } else if (rest.equals(">") || rest.equals("|")
                    || rest.startsWith(">-") || rest.startsWith("|-")
                    || rest.startsWith(">+") || rest.startsWith("|+")) {
                key = k;
                blockScalar = true;
                literal = rest.charAt(0) == '|';
            } else if (rest.startsWith("[")) {
                lists.put(k, inlineList(rest));
            } else {
                scalars.put(k, unquote(rest));
            }
        }
        throw new IllegalStateException("Frontmatter nicht geschlossen (kein zweites ---): " + file);
    }

    private static void flush(Map<String, String> scalars, Map<String, List<String>> lists,
                              String key, boolean blockScalar, boolean literal,
                              List<String> blockLines, List<String> listItems) {
        if (key == null) {
            return;
        }
        if (blockScalar) {
            String joined = literal ? String.join("\n", blockLines) : String.join(" ", blockLines);
            scalars.put(key, joined.trim());
        } else if (!listItems.isEmpty()) {
            lists.put(key, List.copyOf(listItems));
        } else {
            scalars.put(key, ""); // Schluessel ohne Wert (z.B. leere Block-Liste)
        }
    }

    private static List<String> inlineList(String s) {
        List<String> out = new ArrayList<>();
        String v = s.trim();
        if (v.startsWith("[")) {
            v = v.substring(1);
        }
        if (v.endsWith("]")) {
            v = v.substring(0, v.length() - 1);
        }
        for (String part : v.split(",")) {
            String item = unquote(part.trim());
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
