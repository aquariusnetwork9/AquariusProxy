package com.zenith.api.event.player;

import org.geysermc.mcprotocollib.auth.GameProfile;

import java.net.SocketAddress;

public record BlacklistedPlayerConnectedEvent(GameProfile gameProfile, SocketAddress remoteAddress) { }
