package com.zenith.feature.pathfinder.goals;

import static com.zenith.feature.pathfinder.movement.ActionCosts.FALL_N_BLOCKS_COST;
import static com.zenith.feature.pathfinder.movement.ActionCosts.JUMP_ONE_BLOCK_COST;

public record GoalY(int level) implements Goal {
    @Override
    public boolean isInGoal(final int x, final int y, final int z) {
        return this.level == y;
    }

    @Override
    public double heuristic(final int x, final int y, final int z) {
        return calculate(this.level, y);
    }

    public static double calculate(int goalY, int currentY) {
        if (currentY > goalY) {
            // need to descend
            return FALL_N_BLOCKS_COST[2] / 2 * (currentY - goalY);
        }
        if (currentY < goalY) {
            // need to ascend
            return (goalY - currentY) * JUMP_ONE_BLOCK_COST;
        }
        return 0;
    }
}
