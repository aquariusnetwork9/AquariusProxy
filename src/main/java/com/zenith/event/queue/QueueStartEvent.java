package com.zenith.event.queue;

import java.time.Duration;

public record QueueStartEvent(boolean wasOnline, Duration wasOnlineDuration) { }
