package com.zenith.event.player;

import org.geysermc.mcprotocollib.auth.GameProfile;

public record PlayerDisconnectedEvent(String reason, GameProfile clientGameProfile) {

    public PlayerDisconnectedEvent() {
        this(null, null);
    }

    public PlayerDisconnectedEvent(final String reason) {
        this(reason, null);
    }
}
