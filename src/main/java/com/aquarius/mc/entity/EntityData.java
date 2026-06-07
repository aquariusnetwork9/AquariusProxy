package com.aquarius.mc.entity;

import com.aquarius.mc.RegistryData;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.jspecify.annotations.Nullable;

public record EntityData(
    int id,
    String name,
    float width,
    float height,
    boolean attackable,
    boolean pickable,
    boolean livingEntity,
    boolean ageableMob,
    boolean blocksBuilding,
    EntityType mcplType,
    @Nullable EntityAttachment entityAttachment
) implements RegistryData { }
