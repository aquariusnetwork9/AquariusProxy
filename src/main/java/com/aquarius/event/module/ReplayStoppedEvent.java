package com.aquarius.event.module;

import org.jspecify.annotations.Nullable;

import java.io.File;

public record ReplayStoppedEvent(@Nullable File replayFile) { }
