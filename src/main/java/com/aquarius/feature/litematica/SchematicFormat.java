package com.aquarius.feature.litematica;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Detects a schematic file's format by extension and dispatches to the right parser. */
public final class SchematicFormat {
    private SchematicFormat() {}

    /** Supported file extensions (lowercase, with dot). */
    public static boolean isSupported(String fileName) {
        String fn = fileName.toLowerCase(Locale.ROOT);
        return fn.endsWith(".litematic") || fn.endsWith(".nbt");
    }

    public static Schematic load(Path path) throws IOException, LitematicaParseException {
        String fn = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fn.endsWith(".litematic")) return LitematicParser.parse(path);
        if (fn.endsWith(".nbt")) return NbtStructureParser.parse(path);
        throw new LitematicaParseException("Unsupported file type (expected .litematic or .nbt): " + path.getFileName());
    }
}
