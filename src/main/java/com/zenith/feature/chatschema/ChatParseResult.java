package com.zenith.feature.chatschema;

import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.jspecify.annotations.Nullable;

public record ChatParseResult(
    ChatType type,
    @Nullable PlayerListEntry sender,
    @Nullable PlayerListEntry receiver,
    @Nullable String messageContent
) {
}
