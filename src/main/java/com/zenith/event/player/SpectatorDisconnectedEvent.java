package com.zenith.event.player;

import org.geysermc.mcprotocollib.auth.GameProfile;

public record SpectatorDisconnectedEvent(GameProfile clientGameProfile) { }
