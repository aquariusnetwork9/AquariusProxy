package com.zenith.event.proxy;

import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.auth.GameProfile;


public record ProxyClientConnectedEvent(ServerSession session, GameProfile clientGameProfile) { }
