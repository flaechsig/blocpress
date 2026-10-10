package io.github.flaechsig.blocpress.render;

import io.github.flaechsig.blocpress.core.OutputFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Eine warme LibreOffice-Instanz hinter einem Helferprozess ({@code bp-convert.py}, ADR-0021).
 *
 * <p>Der Helfer startet {@code soffice} mit eigenem Profil an einer lokalen Pipe und spricht mit
 * render ueber stdin/stdout: {@code READY}, {@code CONVERT <format> <laenge>} + Bytes,
 * {@code PROGRESS}, {@code OK <laenge>} + Bytes oder {@code FAIL <meldung>}. Ein Lesethread
 * legt die Antworten in eine Warteschlange; {@link #convert} wartet darauf mit dem Zeitlimit ohne
 * Fortschritt und der Hoechstdauer (REQ-0100, REQ-0104). Nicht threadsicher: Der Pool gibt eine
 * Instanz immer nur an einen Aufrufer.</p>
 */
class LibreOfficeInstance {

    private static final Logger LOG = LoggerFactory.getLogger(LibreOfficeInstance.class);

    private enum Kind { READY, PROGRESS, OK, FAIL, EOF }

    private record Event(Kind kind, byte[] payload, String message) {
    }

    private final List<String> command;
    private Process process;
    private OutputStream toHelper;
    private BlockingQueue<Event> events;
    private int conversions;

    LibreOfficeInstance(List<String> command) {
        this.command = command;
    }

    /** Startet Helfer und Instanz und wartet auf {@code READY}. */
    void start(Duration startTimeout) throws IOException {
        destroy();
        conversions = 0;
        process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        toHelper = process.getOutputStream();
        events = new LinkedBlockingQueue<>();
        Thread reader = new Thread(() -> read(process.getInputStream(), events),
                "libreoffice-" + process.pid());
        reader.setDaemon(true);
        reader.start();
        try {
            Event ready = events.poll(startTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (ready == null || ready.kind() != Kind.READY) {
                destroy();
                throw new IOException("LibreOffice instance did not start within " + startTimeout.toSeconds() + " s"
                        + (ready != null && ready.message() != null ? ": " + ready.message() : ""));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroy();
            throw new IOException("Interrupted while starting LibreOffice", e);
        }
        LOG.info("LibreOffice instance started (helper pid {})", process.pid());
    }

    boolean isAlive() {
        return process != null && process.isAlive();
    }

    int conversions() {
        return conversions;
    }

    /** Prozess-ID des Helfers, fuer Tests und Logs. */
    long pid() {
        return process != null ? process.pid() : -1;
    }

    /**
     * Konvertiert in der laufenden Instanz. Bei Zeitueberschreitung oder Absturz ist die Instanz
     * danach beendet ({@link #isAlive()} false); der Pool ersetzt sie.
     */
    byte[] convert(byte[] odt, OutputFormat format, Duration idleTimeout, Duration maxDuration) throws IOException {
        if (!isAlive()) {
            throw new IOException("LibreOffice instance is not running");
        }
        String request = "CONVERT " + suffix(format) + " " + odt.length + "\n";
        try {
            toHelper.write(request.getBytes(StandardCharsets.US_ASCII));
            toHelper.write(odt);
            toHelper.flush();
        } catch (IOException e) {
            destroy();
            throw new IOException("LibreOffice instance terminated unexpectedly", e);
        }
        long deadline = System.nanoTime() + maxDuration.toNanos();
        try {
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    destroy();
                    throw new LibreOfficeTimeoutException("LibreOffice conversion exceeded the maximum duration of "
                            + format(maxDuration));
                }
                long wait = Math.min(idleTimeout.toNanos(), remaining);
                Event event = events.poll(wait, TimeUnit.NANOSECONDS);
                if (event == null) {
                    if (System.nanoTime() >= deadline) {
                        continue; // oben als Hoechstdauer melden
                    }
                    destroy();
                    throw new LibreOfficeTimeoutException("LibreOffice conversion reported no progress for "
                            + format(idleTimeout));
                }
                switch (event.kind()) {
                    case PROGRESS -> { /* weiter warten, Zeitlimit beginnt neu */ }
                    case OK -> {
                        conversions++;
                        return event.payload();
                    }
                    case FAIL -> throw new IOException("LibreOffice conversion failed: " + event.message());
                    case EOF, READY -> {
                        destroy();
                        throw new IOException("LibreOffice instance terminated unexpectedly");
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroy();
            throw new IOException("Interrupted during LibreOffice conversion", e);
        }
    }

    /** Resident Set Size von Helfer und allen Unterprozessen (Instanz), aus {@code /proc}. */
    long rssBytes() {
        if (process == null) {
            return 0;
        }
        ProcessHandle self = process.toHandle();
        return Stream.concat(Stream.of(self), self.descendants())
                .mapToLong(h -> rssOf(h.pid()))
                .sum();
    }

    /** Beendet Helfer und Instanz samt Unterprozessen. */
    void destroy() {
        if (process == null) {
            return;
        }
        ProcessHandle self = process.toHandle();
        self.descendants().forEach(ProcessHandle::destroyForcibly);
        self.destroyForcibly();
        try {
            process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        process = null;
    }

    private static void read(InputStream from, BlockingQueue<Event> to) {
        try (DataInputStream in = new DataInputStream(from)) {
            while (true) {
                String line = readLine(in);
                if (line == null) {
                    break;
                }
                if (line.equals("READY")) {
                    to.add(new Event(Kind.READY, null, null));
                } else if (line.equals("PROGRESS")) {
                    to.add(new Event(Kind.PROGRESS, null, null));
                } else if (line.startsWith("OK ")) {
                    byte[] payload = new byte[Integer.parseInt(line.substring(3).trim())];
                    in.readFully(payload);
                    to.add(new Event(Kind.OK, payload, null));
                } else if (line.startsWith("FAIL")) {
                    to.add(new Event(Kind.FAIL, null, line.substring(4).trim()));
                } else {
                    LOG.warn("Unexpected line from LibreOffice helper: {}", line);
                }
            }
        } catch (IOException | RuntimeException e) {
            // Helfer beendet oder Protokoll verletzt
        }
        to.add(new Event(Kind.EOF, null, null));
    }

    /** Liest eine Zeile bis {@code \n}; {@code null} am Ende des Stroms. */
    private static String readLine(DataInputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int b;
            try {
                b = in.read();
            } catch (EOFException e) {
                b = -1;
            }
            if (b == -1) {
                return line.size() == 0 ? null : line.toString(StandardCharsets.UTF_8);
            }
            if (b == '\n') {
                return line.toString(StandardCharsets.UTF_8);
            }
            line.write(b);
        }
    }

    private static long rssOf(long pid) {
        try (Stream<String> lines = Files.lines(Path.of("/proc", Long.toString(pid), "status"))) {
            return lines.filter(l -> l.startsWith("VmRSS:"))
                    .findFirst()
                    .map(l -> Long.parseLong(l.replaceAll("\\D", "")) * 1024)
                    .orElse(0L);
        } catch (IOException | RuntimeException e) {
            return 0; // Prozess beendet oder kein /proc (nicht Linux)
        }
    }

    private static String suffix(OutputFormat format) {
        return switch (format) {
            case PDF -> "pdf";
            case RTF -> "rtf";
            case ODT -> "odt";
        };
    }

    private static String format(Duration d) {
        return d.toMillis() % 1000 == 0 ? d.toSeconds() + " s" : d.toMillis() + " ms";
    }
}
