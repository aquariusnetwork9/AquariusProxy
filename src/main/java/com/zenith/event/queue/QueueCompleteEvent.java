package com.zenith.event.queue;

import java.time.Duration;

public record QueueCompleteEvent(Duration queueDuration) { }
