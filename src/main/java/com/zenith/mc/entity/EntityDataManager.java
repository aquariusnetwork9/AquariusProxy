package com.zenith.mc.entity;

import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.mc.block.LocalizedCollisionBox;
import com.zenith.util.Maps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;

public class EntityDataManager {
    private final Reference2ObjectMap<EntityType, EntityData> entityTypeToData = new Reference2ObjectOpenHashMap<>(EntityRegistry.REGISTRY.size(), Maps.FAST_LOAD_FACTOR);

    public EntityDataManager() {
        init();
    }

    private void init() {
        for (var entry : EntityRegistry.REGISTRY.getIdMap().int2ObjectEntrySet()) {
            var entity = entry.getValue();
            entityTypeToData.put(entity.mcplType(), entity);
        }
    }

    public EntityData getEntityData(final int id) {
        return EntityRegistry.REGISTRY.get(id);
    }

    public EntityData getEntityData(final EntityType type) {
        var data = entityTypeToData.get(type);
        if (data == entityTypeToData.defaultReturnValue()) return null;
        return data;
    }

    public LocalizedCollisionBox getCollisionBox(final EntityLiving entity) {
        var data = getEntityData(entity.getEntityType());
        if (data == null) return null;
        double w = data.width() / 2;
        return new LocalizedCollisionBox(
            entity.getX() - w, entity.getX() + w,
            entity.getY(), entity.getY() + data.height(),
            entity.getZ() - w, entity.getZ() + w,
            entity.getX(), entity.getY(), entity.getZ()
        );
    }
}
