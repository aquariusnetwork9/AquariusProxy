package com.zenith.api;

import java.util.List;

/**
 * Serialized as JSON into the plugin jar
 */
public record PluginEntrypoint(
    String entrypoint,
    String id,
    List<String> mcVersions
) { }
