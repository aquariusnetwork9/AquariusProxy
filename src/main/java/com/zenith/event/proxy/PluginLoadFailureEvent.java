package com.zenith.event.proxy;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

public record PluginLoadFailureEvent(@Nullable String id, Path jarPath, String message) {
}
