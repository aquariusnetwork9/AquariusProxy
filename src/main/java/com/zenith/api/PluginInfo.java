package com.zenith.api;

import java.util.List;

public record PluginInfo(
    String entrypoint,
    String id,
    String version,
    String description,
    String url,
    List<String> authors,
    List<String> mcVersions
) { }
