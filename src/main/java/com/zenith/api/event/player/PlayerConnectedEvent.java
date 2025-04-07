package com.zenith.api.event.player;

import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.auth.GameProfile;


public record PlayerConnectedEvent(ServerSession session, GameProfile clientGameProfile) { }
