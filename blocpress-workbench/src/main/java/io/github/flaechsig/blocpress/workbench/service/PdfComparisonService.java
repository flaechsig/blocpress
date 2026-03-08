package io.github.flaechsig.blocpress.workbench.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.Base64;

/**
 * Compares two PDFs at the text level (not pixel level).
 *
 * Strategy:
 *   1. pdftohtml -xml extracts each text line with its exact bounding box.
 *   2. Lines present in expected but missing in actual → highlighted in the expected view.
 *   3. Lines present in actual but missing in expected → highlighted in the actual view.
 *   4. pdftoppm renders each page to PNG; ImageMagick draws red rectangles at the diff positions.
 *   5. montage places expected and actual side-by-side.
 *
 * This avoids false positives from anti-aliasing, timestamps, and PDF metadata — only
 * real content differences are highlighted.
 */
@ApplicationScoped
public class PdfComparisonService {

    private static final Logger LOG = Logger.getLogger(PdfComparisonService.class);

    /** Scale factor: pdftoppm at 150 DPI, PDF coordinates in 72 pt/inch */
    private static final float SCALE = 150.0f / 72.0f;

    record TextBlock(String text, float left, float top, float width, float height) {}
    record PageBlocks(List<TextBlock> blocks, float height) {}
    record DiffResult(List<TextBlock> removedFromExpected, List<TextBlock> addedInActual) {}

    /**
     * Ein einzelner Textblock mit Pixelkoordinaten (bei 150 DPI) und Textinhalt.
     * Wird im Frontend für klickbare Overlays verwendet.
     */
    public record DiffBlock(String text, int x1, int y1, int x2, int y2) {}

    /** Per-Seite Ergebnis für den Inline-Diff-Viewer. */
    public record DiffPage(int pageIndex, boolean hasDiff,
                           String expectedBase64, String actualBase64,
                           int pageWidthPx, int pageHeightPx,
                           List<DiffBlock> removedBlocks,   // fehlen im Actual
                           List<DiffBlock> addedBlocks) {}  // fehlen im Expected

    public record DiffPagesReport(int totalPages, int diffCount,
                                  List<Integer> diffPageIndices, List<DiffPage> pages) {}

    // ===== Public API =====

    /**
     * Returns true if both PDFs have identical text content (modulo ignored patterns).
     * Byte-equality is checked first as a fast path.
     */
    public boolean areVisuallyIdentical(byte[] expected, byte[] actual) {
        return areVisuallyIdentical(expected, actual, List.of());
    }

    public boolean areVisuallyIdentical(byte[] expected, byte[] actual, List<String> ignoredPatterns) {
        if (ignoredPatterns.isEmpty() && Arrays.equals(expected, actual)) return true;
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("pdf-cmp-");
            Path expPdf = tmpDir.resolve("expected.pdf");
            Path actPdf = tmpDir.resolve("actual.pdf");
            Files.write(expPdf, expected);
            Files.write(actPdf, actual);
            List<PageBlocks> expPages = extractTextBlocks(expPdf, tmpDir, "exp");
            List<PageBlocks> actPages = extractTextBlocks(actPdf, tmpDir, "act");
            if (expPages.size() != actPages.size()) return false;
            for (int i = 0; i < expPages.size(); i++) {
                DiffResult diff = diffPages(expPages.get(i), actPages.get(i), ignoredPatterns, i);
                if (!diff.removedFromExpected().isEmpty() || !diff.addedInActual().isEmpty()) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            LOG.warnf("Text comparison failed, falling back to byte comparison: %s", e.getMessage());
            return Arrays.equals(expected, actual);
        } finally {
            deleteTempDir(tmpDir);
        }
    }

    /**
     * Generates per-page diff images for inline display.
     * Each page is returned as two base64-encoded PNGs (expected + actual with highlights).
     */
    public DiffPagesReport generateDiffPages(byte[] expected, byte[] actual,
                                             List<String> ignoredPatterns) {
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("pdf-diffp-");
            Path expPdf = tmpDir.resolve("expected.pdf");
            Path actPdf = tmpDir.resolve("actual.pdf");
            Files.write(expPdf, expected);
            Files.write(actPdf, actual);

            List<PageBlocks> expPages = extractTextBlocks(expPdf, tmpDir, "exp");
            List<PageBlocks> actPages = extractTextBlocks(actPdf, tmpDir, "act");

            Path expImgDir = tmpDir.resolve("exp-img");
            Path actImgDir = tmpDir.resolve("act-img");
            Files.createDirectories(expImgDir);
            Files.createDirectories(actImgDir);
            renderPages(expPdf, expImgDir, "page");
            renderPages(actPdf, actImgDir, "page");

            List<Path> expImages = sortedPngs(expImgDir);
            List<Path> actImages = sortedPngs(actImgDir);
            int pageCount = Math.max(expImages.size(), actImages.size());

            List<DiffPage> pages = new ArrayList<>();
            List<Integer> diffPageIndices = new ArrayList<>();

            for (int i = 0; i < pageCount; i++) {
                Path expImg = i < expImages.size() ? expImages.get(i) : blankPage(tmpDir, i, "exp");
                Path actImg = i < actImages.size() ? actImages.get(i) : blankPage(tmpDir, i, "act");

                PageBlocks eb = i < expPages.size() ? expPages.get(i) : new PageBlocks(List.of(), 842);
                PageBlocks ab = i < actPages.size() ? actPages.get(i) : new PageBlocks(List.of(), 842);

                DiffResult diff = diffPages(eb, ab, ignoredPatterns, i);
                boolean hasDiff = !diff.removedFromExpected().isEmpty() || !diff.addedInActual().isEmpty();

                Path expHl = tmpDir.resolve("exp-hl-" + i + ".png");
                Path actHl = tmpDir.resolve("act-hl-" + i + ".png");
                drawHighlights(expImg, diff.removedFromExpected(), expHl);
                drawHighlights(actImg, diff.addedInActual(), actHl);

                String expB64 = Base64.getEncoder().encodeToString(Files.readAllBytes(expHl));
                String actB64 = Base64.getEncoder().encodeToString(Files.readAllBytes(actHl));

                int pageWidthPx  = (int)(595 * SCALE); // fallback A4; overwritten below if known
                int pageHeightPx = (int)(eb.height() > 0 ? eb.height() * SCALE : 842 * SCALE);

                List<DiffBlock> removedBlocks = toDiffBlocks(diff.removedFromExpected());
                List<DiffBlock> addedBlocks   = toDiffBlocks(diff.addedInActual());

                pages.add(new DiffPage(i, hasDiff, expB64, actB64,
                    pageWidthPx, pageHeightPx, removedBlocks, addedBlocks));
                if (hasDiff) diffPageIndices.add(i);
            }

            return new DiffPagesReport(pageCount, diffPageIndices.size(), diffPageIndices, pages);
        } catch (Exception e) {
            LOG.errorf("generateDiffPages failed: %s", e.getMessage());
            return null;
        } finally {
            deleteTempDir(tmpDir);
        }
    }

    /**
     * Generates a side-by-side diff PDF.
     * Differing text lines are highlighted with a red box on each page.
     * Returns null if generation fails.
     */
    public byte[] generateDiffPdf(byte[] expected, byte[] actual) {
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("pdf-diff-");
            Path expPdf = tmpDir.resolve("expected.pdf");
            Path actPdf = tmpDir.resolve("actual.pdf");
            Files.write(expPdf, expected);
            Files.write(actPdf, actual);

            // Extract text blocks (position + content) from each PDF
            List<PageBlocks> expPages = extractTextBlocks(expPdf, tmpDir, "exp");
            List<PageBlocks> actPages = extractTextBlocks(actPdf, tmpDir, "act");

            // Render pages to PNG images at 150 DPI
            Path expImgDir = tmpDir.resolve("exp-img");
            Path actImgDir = tmpDir.resolve("act-img");
            Files.createDirectories(expImgDir);
            Files.createDirectories(actImgDir);
            renderPages(expPdf, expImgDir, "page");
            renderPages(actPdf, actImgDir, "page");

            List<Path> expImages = sortedPngs(expImgDir);
            List<Path> actImages = sortedPngs(actImgDir);

            int pageCount = Math.max(expImages.size(), actImages.size());
            List<Path> composites = new ArrayList<>();

            for (int i = 0; i < pageCount; i++) {
                Path expImg = i < expImages.size() ? expImages.get(i) : blankPage(tmpDir, i, "exp");
                Path actImg = i < actImages.size() ? actImages.get(i) : blankPage(tmpDir, i, "act");

                PageBlocks eb = i < expPages.size() ? expPages.get(i) : new PageBlocks(List.of(), 842);
                PageBlocks ab = i < actPages.size() ? actPages.get(i) : new PageBlocks(List.of(), 842);

                DiffResult diff = diffPages(eb, ab, List.of(), i);

                Path expHl = tmpDir.resolve("exp-hl-" + i + ".png");
                Path actHl = tmpDir.resolve("act-hl-" + i + ".png");
                drawHighlights(expImg, diff.removedFromExpected(), expHl);
                drawHighlights(actImg, diff.addedInActual(), actHl);

                Path composite = tmpDir.resolve("composite-" + i + ".png");
                sideBySide(expHl, actHl, composite);
                composites.add(composite);
            }

            Path outputPdf = tmpDir.resolve("diff-output.pdf");
            pngsToPdf(composites, outputPdf);
            return Files.readAllBytes(outputPdf);
        } catch (Exception e) {
            LOG.errorf("Failed to generate diff PDF: %s", e.getMessage());
            return null;
        } finally {
            deleteTempDir(tmpDir);
        }
    }

    // ===== Text extraction =====

    private String extractText(byte[] pdf) throws IOException, InterruptedException {
        Path tmp = Files.createTempFile("pdf-txt-", ".pdf");
        try {
            Files.write(tmp, pdf);
            Process p = new ProcessBuilder(
                "pdftotext", tmp.toAbsolutePath().toString(), "-"
            ).redirectErrorStream(false).start();
            String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            return text;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Runs pdftohtml -xml to get text lines with bounding boxes, then parses the XML.
     */
    private List<PageBlocks> extractTextBlocks(Path pdf, Path tmpDir, String prefix)
            throws IOException, InterruptedException {
        Path xmlDir = Files.createTempDirectory(tmpDir, prefix + "-xml-");

        // -zoom 1 forces coordinates to 72 DPI (PDF points), matching our SCALE = 150/72.
        // Default zoom=1.5 would produce 108-DPI coordinates and shift highlights by ×1.5.
        Process p = new ProcessBuilder(
            "pdftohtml", "-xml", "-q", "-noframes", "-zoom", "1",
            pdf.toAbsolutePath().toString(),
            xmlDir.resolve(prefix).toAbsolutePath().toString()
        ).redirectErrorStream(true).start();
        p.getInputStream().transferTo(OutputStream.nullOutputStream());
        p.waitFor();

        // pdftohtml writes <prefix>.xml into xmlDir — find it defensively
        Path xmlFile;
        try (var stream = Files.list(xmlDir)) {
            xmlFile = stream.filter(f -> f.toString().endsWith(".xml")).findFirst().orElse(null);
        }
        if (xmlFile == null) {
            LOG.warnf("pdftohtml produced no XML in %s", xmlDir);
            return List.of();
        }
        return parseBlocksXml(xmlFile);
    }

    private List<PageBlocks> parseBlocksXml(Path xmlFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external DTD loading (pdf2xml.dtd) to avoid network access
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            Document doc = factory.newDocumentBuilder().parse(xmlFile.toFile());

            NodeList pages = doc.getElementsByTagName("page");
            List<PageBlocks> result = new ArrayList<>();

            for (int i = 0; i < pages.getLength(); i++) {
                Element page = (Element) pages.item(i);
                float pageHeight = floatAttr(page, "height", 842);

                NodeList texts = page.getElementsByTagName("text");
                List<TextBlock> blocks = new ArrayList<>();
                for (int j = 0; j < texts.getLength(); j++) {
                    Element text = (Element) texts.item(j);
                    String content = text.getTextContent().trim();
                    if (!content.isEmpty()) {
                        blocks.add(new TextBlock(
                            content,
                            floatAttr(text, "left", 0),
                            floatAttr(text, "top", 0),
                            floatAttr(text, "width", 0),
                            floatAttr(text, "height", 12)
                        ));
                    }
                }
                result.add(new PageBlocks(blocks, pageHeight));
            }
            return result;
        } catch (Exception e) {
            LOG.warnf("Could not parse pdftohtml XML %s: %s", xmlFile, e.getMessage());
            return List.of();
        }
    }

    private float floatAttr(Element el, String attr, float defaultVal) {
        try { return Float.parseFloat(el.getAttribute(attr)); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    // ===== Diff =====

    /**
     * Set-based diff: a text line is considered "different" if it does not appear
     * (anywhere on the page) in the other document. Blocks matching any ignored pattern
     * or ignored area (position-based) are excluded from comparison.
     */
    private DiffResult diffPages(PageBlocks expected, PageBlocks actual,
                                 List<String> ignoredPatterns, int pageIndex) {
        List<Pattern> compiled = compilePatterns(ignoredPatterns);
        List<AreaIgnore> areas = parseAreaIgnores(ignoredPatterns, pageIndex);

        List<TextBlock> expBlocks = expected.blocks().stream()
            .filter(b -> !matchesAny(b.text(), compiled) && !matchesArea(b, areas)).toList();
        List<TextBlock> actBlocks = actual.blocks().stream()
            .filter(b -> !matchesAny(b.text(), compiled) && !matchesArea(b, areas)).toList();

        Set<String> expTexts = expBlocks.stream().map(TextBlock::text).collect(Collectors.toSet());
        Set<String> actTexts = actBlocks.stream().map(TextBlock::text).collect(Collectors.toSet());

        return new DiffResult(
            expBlocks.stream().filter(b -> !actTexts.contains(b.text())).toList(),
            actBlocks.stream().filter(b -> !expTexts.contains(b.text())).toList()
        );
    }

    /** Parses "@area:{page}:{x1}:{y1}:{x2}:{y2}" patterns for the given page. */
    private List<AreaIgnore> parseAreaIgnores(List<String> patterns, int pageIndex) {
        if (patterns == null) return List.of();
        List<AreaIgnore> result = new ArrayList<>();
        for (String p : patterns) {
            if (!p.startsWith("@area:")) continue;
            String[] parts = p.substring(6).split(":");
            if (parts.length != 5) continue;
            try {
                if (Integer.parseInt(parts[0]) == pageIndex) {
                    result.add(new AreaIgnore(
                        Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]), Integer.parseInt(parts[4])));
                }
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private record AreaIgnore(int x1, int y1, int x2, int y2) {}

    /** Returns true when the center of the block falls inside an ignored area. */
    private boolean matchesArea(TextBlock block, List<AreaIgnore> areas) {
        if (areas.isEmpty()) return false;
        int bx1 = Math.max(0, (int)((block.left()  - 2) * SCALE));
        int by1 = Math.max(0, (int)((block.top()   - 2) * SCALE));
        int bx2 = (int)((block.left() + block.width()  + 2) * SCALE);
        int by2 = (int)((block.top()  + block.height() + 2) * SCALE);
        int cx  = (bx1 + bx2) / 2;
        int cy  = (by1 + by2) / 2;
        for (AreaIgnore a : areas) {
            if (cx >= a.x1() && cx <= a.x2() && cy >= a.y1() && cy <= a.y2()) return true;
        }
        return false;
    }

    private List<DiffBlock> toDiffBlocks(List<TextBlock> blocks) {
        return blocks.stream().map(b -> new DiffBlock(
            b.text(),
            Math.max(0, (int)((b.left()  - 2) * SCALE)),
            Math.max(0, (int)((b.top()   - 2) * SCALE)),
            (int)((b.left() + b.width()  + 2) * SCALE),
            (int)((b.top()  + b.height() + 2) * SCALE)
        )).toList();
    }

    private List<Pattern> compilePatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return List.of();
        List<Pattern> result = new ArrayList<>();
        for (String p : patterns) {
            if (p.startsWith("@area:")) continue; // handled separately as area ignore
            try { result.add(Pattern.compile(p)); }
            catch (PatternSyntaxException e) {
                result.add(Pattern.compile(Pattern.quote(p)));
            }
        }
        return result;
    }

    private boolean matchesAny(String text, List<Pattern> patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(text).find()) return true;
        }
        return false;
    }

    // ===== Rendering =====

    private void renderPages(Path pdfFile, Path outputDir, String prefix)
            throws IOException, InterruptedException {
        Process p = new ProcessBuilder(
            "pdftoppm", "-r", "150", "-png",
            pdfFile.toAbsolutePath().toString(),
            outputDir.resolve(prefix).toAbsolutePath().toString()
        ).redirectErrorStream(true).start();
        p.getInputStream().transferTo(OutputStream.nullOutputStream());
        int exit = p.waitFor();
        if (exit != 0) throw new IOException("pdftoppm failed (exit " + exit + ")");
    }

    /**
     * Draws semi-transparent red rectangles around each differing text block.
     * Coordinates are in PDF points; SCALE converts them to pixel coordinates at 150 DPI.
     */
    private void drawHighlights(Path baseImage, List<TextBlock> blocks, Path output)
            throws IOException, InterruptedException {
        if (blocks.isEmpty()) {
            Files.copy(baseImage, output, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("convert");
        cmd.add(baseImage.toAbsolutePath().toString());
        cmd.add("-fill");   cmd.add("rgba(255,60,60,0.35)");
        cmd.add("-stroke"); cmd.add("rgb(200,0,0)");
        cmd.add("-strokewidth"); cmd.add("1");

        for (TextBlock b : blocks) {
            int x1 = Math.max(0, (int)((b.left() - 2) * SCALE));
            int y1 = Math.max(0, (int)((b.top()  - 2) * SCALE));
            int x2 = (int)((b.left() + b.width()  + 2) * SCALE);
            int y2 = (int)((b.top()  + b.height() + 2) * SCALE);
            cmd.add("-draw");
            cmd.add("rectangle " + x1 + "," + y1 + " " + x2 + "," + y2);
        }
        cmd.add(output.toAbsolutePath().toString());

        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getInputStream().transferTo(OutputStream.nullOutputStream());
        p.waitFor();
    }

    private void sideBySide(Path left, Path right, Path output)
            throws IOException, InterruptedException {
        Process p = new ProcessBuilder(
            "montage",
            "-label", "Erwartet", left.toAbsolutePath().toString(),
            "-label", "Aktuell",   right.toAbsolutePath().toString(),
            "-tile", "2x1",
            "-geometry", "+6+6",
            "-background", "#e8e8e8",
            "-font", "DejaVu-Sans",
            "-pointsize", "22",
            output.toAbsolutePath().toString()
        ).redirectErrorStream(true).start();
        p.getInputStream().transferTo(OutputStream.nullOutputStream());
        int exit = p.waitFor();
        if (exit != 0) throw new IOException("montage failed (exit " + exit + ")");
    }

    private void pngsToPdf(List<Path> images, Path outputPdf)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("convert");
        for (Path img : images) cmd.add(img.toAbsolutePath().toString());
        cmd.add(outputPdf.toAbsolutePath().toString());
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getInputStream().transferTo(OutputStream.nullOutputStream());
        int exit = p.waitFor();
        if (exit != 0) throw new IOException("convert failed creating PDF (exit " + exit + ")");
    }

    private Path blankPage(Path tmpDir, int index, String suffix) throws IOException {
        Path blank = tmpDir.resolve("blank-" + index + "-" + suffix + ".png");
        Process p = new ProcessBuilder(
            "convert", "-size", "1240x1754", "xc:white", blank.toAbsolutePath().toString()
        ).redirectErrorStream(true).start();
        try { p.waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return blank;
    }

    private List<Path> sortedPngs(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream
                .filter(p -> p.toString().endsWith(".png"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }
    }

    private void deleteTempDir(Path dir) {
        if (dir == null) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                  .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
