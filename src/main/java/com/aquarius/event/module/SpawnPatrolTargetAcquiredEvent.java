package com.aquarius.event.module;

import com.aquarius.cache.data.entity.EntityPlayer;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

public record SpawnPatrolTargetAcquiredEvent(EntityPlayer target, PlayerListEntry targetProfile) {
}
