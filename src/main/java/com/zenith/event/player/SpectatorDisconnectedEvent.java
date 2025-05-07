package com.zenith.event.player;

import org.geysermc.mcprotocollib.auth.GameProfile;

public record SpectatorDisconnectedEvent(com.zenith.network.server.ServerSession serverSession, GameProfile clientGameProfile) { }
