package com.zenith.event.client;

import java.time.Duration;

import static com.zenith.util.DisconnectMessages.MANUAL_DISCONNECT;

public record ClientDisconnectEvent(
    String reason,
    boolean manualDisconnect,
    Duration onlineDuration,
    Duration onlineDurationWithQueueSkip,
    boolean wasInQueue,
    int queuePosition
) {
    public ClientDisconnectEvent(String reason, final Duration onlineDuration, Duration onlineDurationWithQueueSkip, boolean wasInQueue, int queuePosition) {
        this(reason, (MANUAL_DISCONNECT.equals(reason)), onlineDuration, onlineDurationWithQueueSkip, wasInQueue, queuePosition);
    }

    public ClientDisconnectEvent(String reason) {
        this(reason, Duration.ZERO, Duration.ZERO, false, 0);
    }
}
