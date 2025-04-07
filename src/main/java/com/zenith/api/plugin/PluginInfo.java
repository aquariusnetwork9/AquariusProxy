package com.zenith.api.plugin;

import org.jspecify.annotations.NullMarked;

import java.util.List;

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
) { }
