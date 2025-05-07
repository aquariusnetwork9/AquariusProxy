package com.zenith.event.server;

import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

public record ServerPlayerDisconnectedEvent(PlayerListEntry playerEntry) { }
