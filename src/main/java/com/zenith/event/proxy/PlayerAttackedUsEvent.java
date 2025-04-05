package com.zenith.event.proxy;

import com.zenith.cache.data.entity.EntityPlayer;
import org.cloudburstmc.math.vector.Vector3d;
import org.jspecify.annotations.Nullable;

public record PlayerAttackedUsEvent(EntityPlayer attacker, @Nullable Vector3d sourcePosition) {
}
