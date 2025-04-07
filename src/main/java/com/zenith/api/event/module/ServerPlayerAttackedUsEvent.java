package com.zenith.api.event.module;

import com.zenith.cache.data.entity.EntityPlayer;
import org.cloudburstmc.math.vector.Vector3d;
import org.jspecify.annotations.Nullable;

public record ServerPlayerAttackedUsEvent(EntityPlayer attacker, @Nullable Vector3d sourcePosition) {
}
