package com.zenith.api.event.module;

import org.jspecify.annotations.Nullable;

import java.io.File;

public record ReplayStoppedEvent(@Nullable File replayFile) { }
