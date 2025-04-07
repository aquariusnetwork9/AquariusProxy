package com.zenith.api.event.queue;

import java.time.Duration;

public record QueueStartEvent(boolean wasOnline, Duration wasOnlineDuration) { }
