package com.zenith.mc.block.properties;

import com.zenith.mc.block.Direction;
import com.zenith.mc.block.properties.api.StringRepresentable;

public enum FrontAndTop implements StringRepresentable {
    DOWN_EAST("down_east", Direction.DOWN, Direction.EAST),
    DOWN_NORTH("down_north", Direction.DOWN, Direction.NORTH),
    DOWN_SOUTH("down_south", Direction.DOWN, Direction.SOUTH),
    DOWN_WEST("down_west", Direction.DOWN, Direction.WEST),
    UP_EAST("up_east", Direction.UP, Direction.EAST),
    UP_NORTH("up_north", Direction.UP, Direction.NORTH),
    UP_SOUTH("up_south", Direction.UP, Direction.SOUTH),
    UP_WEST("up_west", Direction.UP, Direction.WEST),
    WEST_UP("west_up", Direction.WEST, Direction.UP),
    EAST_UP("east_up", Direction.EAST, Direction.UP),
    NORTH_UP("north_up", Direction.NORTH, Direction.UP),
    SOUTH_UP("south_up", Direction.SOUTH, Direction.UP);

    private final String name;
    private final Direction top;
    private final Direction front;

    private static int lookupKey(Direction front, Direction top) {
        return top.ordinal() << 3 | front.ordinal();
    }

    private FrontAndTop(final String name, final Direction front, final Direction top) {
        this.name = name;
        this.front = front;
        this.top = top;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public Direction front() {
        return this.front;
    }

    public Direction top() {
        return this.top;
    }
}

