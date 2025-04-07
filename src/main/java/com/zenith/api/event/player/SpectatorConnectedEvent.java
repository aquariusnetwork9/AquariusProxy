package com.zenith.api.event.player;

import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.auth.GameProfile;

public record SpectatorConnectedEvent(ServerSession session, GameProfile clientGameProfile) { }
