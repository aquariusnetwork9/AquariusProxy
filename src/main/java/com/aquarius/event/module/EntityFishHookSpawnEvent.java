package com.aquarius.event.module;

import com.aquarius.cache.data.entity.EntityStandard;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.ProjectileData;

public record EntityFishHookSpawnEvent(EntityStandard fishHookObject) {

    public ProjectileData getProjectileData() {
        return (ProjectileData) fishHookObject().getObjectData();
    }

    public int getOwnerEntityId() {
        return getProjectileData().getOwnerId();
    }
}
