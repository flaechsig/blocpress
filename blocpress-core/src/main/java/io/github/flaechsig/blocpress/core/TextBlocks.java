package io.github.flaechsig.blocpress.core;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hilfen rund um verknuepfte Bausteine (ADR-0018).
 */
public final class TextBlocks {

    /** Pfad endet auf {@code bausteine/{name}.odt}; Host und Praefix sind ohne Bedeutung. */
    private static final Pattern BUILDING_BLOCK_PATH = Pattern.compile("(?:^|/)bausteine/([^/]+)\\.odt$");

    private TextBlocks() {
    }

    /**
     * Liefert den Namen des Bausteins, auf den eine Verknuepfung zeigt.
     *
     * @param href Wert von {@code xlink:href}, z.B.
     *             {@code http://host/api/webdav/released/bausteine/Bankverbindung.odt}
     * @return der URL-dekodierte Name, oder leer, wenn der Pfad nicht auf
     *         {@code /bausteine/{name}.odt} endet
     */
    public static Optional<String> nameOf(String href) {
        if (href == null || href.isBlank()) {
            return Optional.empty();
        }
        String path = href.strip();
        int cut = indexOfAny(path, '?', '#');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        Matcher m = BUILDING_BLOCK_PATH.matcher(path);
        if (!m.find()) {
            return Optional.empty();
        }
        String name;
        try {
            // '+' bleibt ein Plus; nur %-Kodierung wird aufgeloest
            name = URLDecoder.decode(m.group(1).replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (name.isBlank() || name.contains("/") || name.contains("\\")) {
            return Optional.empty();
        }
        return Optional.of(name);
    }

    private static int indexOfAny(String s, char a, char b) {
        int i = s.indexOf(a);
        int j = s.indexOf(b);
        if (i < 0) return j;
        if (j < 0) return i;
        return Math.min(i, j);
    }
}
