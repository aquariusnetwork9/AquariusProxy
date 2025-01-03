package com.zenith.mc.block;

import com.google.common.collect.ImmutableList;
import org.cloudburstmc.math.vector.Vector3i;

import java.util.List;

public enum Direction {
    DOWN(Vector3i.from(0, -1, 0)),
    UP(Vector3i.from(0, 1, 0)),
    NORTH(Vector3i.from(0, 0, -1)),
    SOUTH(Vector3i.from(0, 0, 1)),
    WEST(Vector3i.from(-1, 0, 0)),
    EAST(Vector3i.from(1, 0, 0));

    private final Vector3i normal;
    private Direction(Vector3i normal) {
        this.normal = normal;
    }

    public static final List<Direction> HORIZONTALS = ImmutableList.of(
        Direction.NORTH,
        Direction.EAST,
        Direction.SOUTH,
        Direction.WEST
    );

    public int x() {
        return this.normal.getX();
    }

    public int y() {
        return this.normal.getY();
    }

    public int z() {
        return this.normal.getZ();
    }

    public Vector3i getNormal() {
        return this.normal;
    }

    public org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction mcpl() {
        return switch (this) {
            case DOWN -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.DOWN;
            case UP -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.UP;
            case NORTH -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.NORTH;
            case SOUTH -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.SOUTH;
            case WEST -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.WEST;
            case EAST -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.EAST;
        };
    }
}
