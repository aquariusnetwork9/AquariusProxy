package com.aquarius.event.queue;

import java.time.Duration;

public record QueueCompleteEvent(Duration queueDuration) { }
