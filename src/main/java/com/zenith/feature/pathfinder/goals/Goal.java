package com.zenith.feature.pathfinder.goals;

import com.zenith.mc.block.BlockPos;

public interface Goal {

    boolean isInGoal(int x, int y, int z);

    double heuristic(int x, int y, int z);

    default boolean isInGoal(BlockPos pos) {
        return this.isInGoal(pos.x(), pos.y(), pos.z());
    }

    default double heuristic(BlockPos pos) {
        return this.heuristic(pos.x(), pos.y(), pos.z());
    }

    default double heuristic() {
        return 0;
    }
}
