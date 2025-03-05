package com.zenith.api;

import java.util.List;

/**
 * Data from each plugin's {@link Plugin} annotation.
 */
public record PluginInfo(
    String entrypoint,
    String id,
    String version,
    String description,
    String url,
    List<String> authors,
    List<String> mcVersions
) { }
