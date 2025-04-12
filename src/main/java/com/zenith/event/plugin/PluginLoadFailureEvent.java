package com.zenith.event.plugin;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

public record PluginLoadFailureEvent(@Nullable String id, Path jarPath, Throwable exception) {
    public String message() {
        var cause = exception;
        while (cause != null && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
