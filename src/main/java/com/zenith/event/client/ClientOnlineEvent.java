package com.zenith.event.client;

import java.time.Duration;
import java.util.Optional;

public record ClientOnlineEvent(Optional<Duration> queueWait) {

    public ClientOnlineEvent() {
        this(Optional.empty());
    }
}
