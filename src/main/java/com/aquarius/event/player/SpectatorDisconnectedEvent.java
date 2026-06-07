package com.aquarius.event.player;

import org.geysermc.mcprotocollib.auth.GameProfile;

public record SpectatorDisconnectedEvent(com.aquarius.network.server.ServerSession serverSession, GameProfile clientGameProfile) { }
