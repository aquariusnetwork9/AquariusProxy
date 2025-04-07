package com.zenith.api.event.module;

import com.zenith.cache.data.entity.EntityPlayer;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

public record ServerPlayerLeftVisualRangeEvent(PlayerListEntry playerEntry, EntityPlayer playerEntity) { }
