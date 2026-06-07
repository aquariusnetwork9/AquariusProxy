package com.aquarius.command.brigadier;

import com.aquarius.command.api.CommandContext;
import com.aquarius.mc.block.BlockPos;
import org.cloudburstmc.math.vector.Vector2d;
import org.cloudburstmc.math.vector.Vector3d;

public interface Coordinates {
    Vector3d getPosition(CommandContext source);

    Vector2d getRotation(CommandContext source);

    default BlockPos getBlockPos(CommandContext source) {
        var vec3d = this.getPosition(source);
        return new BlockPos(vec3d.getX(), vec3d.getY(), vec3d.getZ());
    }

    boolean isXRelative();

    boolean isYRelative();

    boolean isZRelative();
}
