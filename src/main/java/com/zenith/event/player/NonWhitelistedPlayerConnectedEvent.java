package com.zenith.event.player;

import org.geysermc.mcprotocollib.auth.GameProfile;

import java.net.SocketAddress;

public record NonWhitelistedPlayerConnectedEvent(GameProfile gameProfile, SocketAddress remoteAddress) { }
