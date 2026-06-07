package com.aquarius.event.player;

import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.auth.GameProfile;


public record PlayerConnectedEvent(ServerSession session, GameProfile clientGameProfile) { }
