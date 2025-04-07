package com.zenith.util;

import com.zenith.api.event.client.ClientTickEvent;
import lombok.Getter;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.EVENT_BUS;

public final class TickTimerManager {
    public static final TickTimerManager INSTANCE = new TickTimerManager();

    @Getter
    private volatile long tickTime = 0;

    private TickTimerManager() {
        EVENT_BUS.subscribe(
            this,
            of(ClientTickEvent.class, 100_000_000, this::onClientTick)
        );
    }

    private void onClientTick(ClientTickEvent event) {
        tickTime++;
    }
}
