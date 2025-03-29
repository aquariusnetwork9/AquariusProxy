package com.zenith.event.proxy;

import java.time.Duration;

import static com.zenith.util.DisconnectMessages.MANUAL_DISCONNECT;

public record DisconnectEvent(
    String reason,
    boolean manualDisconnect,
    Duration onlineDuration,
    Duration onlineDurationWithQueueSkip,
    boolean wasInQueue,
    int queuePosition
) {
    public DisconnectEvent(String reason, final Duration onlineDuration, Duration onlineDurationWithQueueSkip, boolean wasInQueue, int queuePosition) {
        this(reason, (MANUAL_DISCONNECT.equals(reason)), onlineDuration, onlineDurationWithQueueSkip, wasInQueue, queuePosition);
    }

    public DisconnectEvent(String reason) {
        this(reason, Duration.ZERO, Duration.ZERO, false, 0);
    }
}
