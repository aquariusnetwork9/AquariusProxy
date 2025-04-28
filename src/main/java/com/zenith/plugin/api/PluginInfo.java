package com.zenith.plugin.api;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Data from each plugin's {@link Plugin} annotation.
 */
@NullMarked
public record PluginInfo(
    String entrypoint,
    String id,
    String version,
    String description,
    String url,
    List<String> authors,
    List<String> mcVersions
) {
    public static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9-_]{0,63}");
}
