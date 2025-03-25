package com.zenith.event.module;

import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

public record ChatControlExecuteEvent(PlayerListEntry sender, String command, String[] input, boolean success) { }
