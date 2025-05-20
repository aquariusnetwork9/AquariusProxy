package com.zenith.event.chat;

import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

public record PublicChatEvent(
    PlayerListEntry sender,
    // full component as sent by the server, including all formatting
    Component component,
    // extracted message content, i.e. without "<playerName> " prefix
    String message
) { }
