package io.github.flaechsig.blocpress.core;

/**
 * Ein verknuepfter Abschnitt darf nicht eingesetzt werden (ADR-0018). Die Meldung ist fuer den
 * Aufrufer bestimmt und enthaelt keine technischen Details (Host, Verbindungsfehler).
 */
public class TextBlockRejectedException extends RuntimeException {

    public TextBlockRejectedException(String message) {
        super(message);
    }
}
