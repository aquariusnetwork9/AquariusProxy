package com.aquarius.feature.litematica;

/** Thrown when a schematic file cannot be read or is structurally invalid. Surfaced to the user as an error. */
public class LitematicaParseException extends Exception {
    public LitematicaParseException(String message) {
        super(message);
    }

    public LitematicaParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
