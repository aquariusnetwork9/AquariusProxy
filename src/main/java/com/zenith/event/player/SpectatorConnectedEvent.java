package com.zenith.event.player;

import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.auth.GameProfile;

public record SpectatorConnectedEvent(ServerSession session, GameProfile clientGameProfile) { }
