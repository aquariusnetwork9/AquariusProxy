package com.zenith.event.queue;

// note: this may be posted before StartQueueEvent
public record QueueSkipEvent() {
    public static final QueueSkipEvent INSTANCE = new QueueSkipEvent();
}
