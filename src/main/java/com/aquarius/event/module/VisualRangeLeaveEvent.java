package com.aquarius.event.module;

import com.aquarius.cache.data.entity.EntityPlayer;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

public record VisualRangeLeaveEvent(PlayerListEntry playerEntry, EntityPlayer playerEntity, boolean isFriend) { }
