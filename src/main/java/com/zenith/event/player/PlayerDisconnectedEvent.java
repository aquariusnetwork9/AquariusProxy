package com.zenith.event.player;

import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.jspecify.annotations.Nullable;

public record PlayerDisconnectedEvent(@Nullable String reason, ServerSession session, @Nullable GameProfile clientGameProfile) {

    public PlayerDisconnectedEvent(final String reason, ServerSession session) {
        this(reason, session, null);
    }
}
