package com.aquarius.event.player;

import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.auth.GameProfile;

public record SpectatorConnectedEvent(ServerSession session, GameProfile clientGameProfile) { }
