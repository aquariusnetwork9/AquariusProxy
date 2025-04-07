package com.zenith.api.event.queue;

// note: this may be posted before StartQueueEvent
public record QueueSkipEvent() {
    public static final QueueSkipEvent INSTANCE = new QueueSkipEvent();
}
