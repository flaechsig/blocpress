package io.github.flaechsig.blocpress.render;

import java.io.IOException;

/** Eine Konvertierung hat das Zeitlimit ohne Fortschritt oder die Hoechstdauer ueberschritten (REQ-0100, REQ-0104). */
public class LibreOfficeTimeoutException extends IOException {

    public LibreOfficeTimeoutException(String message) {
        super(message);
    }
}
