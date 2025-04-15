package com.zenith.event.player;

import org.geysermc.mcprotocollib.auth.GameProfile;
import org.jspecify.annotations.Nullable;

public record PlayerDisconnectedEvent(@Nullable String reason, @Nullable GameProfile clientGameProfile) {

    public PlayerDisconnectedEvent() {
        this(null, null);
    }

    public PlayerDisconnectedEvent(final String reason) {
        this(reason, null);
    }
}
