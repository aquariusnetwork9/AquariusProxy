package com.zenith.util;

import com.zenith.event.module.ClientTickEvent;
import lombok.Getter;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Shared.EVENT_BUS;

public final class TickTimerManager {
    public static final TickTimerManager INSTANCE = new TickTimerManager();

    @Getter
    private long tickTime = 0;

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
