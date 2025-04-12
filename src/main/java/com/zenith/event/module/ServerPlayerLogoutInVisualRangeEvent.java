package com.zenith.event.module;

import com.zenith.cache.data.entity.EntityPlayer;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

public record ServerPlayerLogoutInVisualRangeEvent(PlayerListEntry playerEntry, EntityPlayer playerEntity) { }
