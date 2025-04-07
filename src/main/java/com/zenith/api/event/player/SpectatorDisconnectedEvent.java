package com.zenith.api.event.player;

import org.geysermc.mcprotocollib.auth.GameProfile;

public record SpectatorDisconnectedEvent(GameProfile clientGameProfile) { }
