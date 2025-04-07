package com.zenith.api.event.queue;

import java.time.Duration;

public record QueueCompleteEvent(Duration queueDuration) { }
