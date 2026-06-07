package com.aquarius.event.queue;

import java.time.Duration;

public record QueueStartEvent(boolean wasOnline, Duration wasOnlineDuration) { }
