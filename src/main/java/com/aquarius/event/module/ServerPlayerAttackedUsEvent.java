package com.aquarius.event.module;

import com.aquarius.cache.data.entity.EntityPlayer;
import org.cloudburstmc.math.vector.Vector3d;
import org.jspecify.annotations.Nullable;

public record ServerPlayerAttackedUsEvent(EntityPlayer attacker, @Nullable Vector3d sourcePosition) {
}
