package com.zenith.feature.pathfinder.goals;

import com.zenith.mc.block.BlockPos;

public record GoalBlock(int x, int y, int z) implements Goal, PosGoal {

    public GoalBlock(BlockPos pos) {
        this(pos.x(), pos.y(), pos.z());
    }

    @Override
    public boolean isInGoal(final int x, final int y, final int z) {
        return this.x == x && this.y == y && this.z == z;
    }

    @Override
    public double heuristic(final int x, final int y, final int z) {
        int xDiff = x - this.x;
        int yDiff = y - this.y;
        int zDiff = z - this.z;
        return calculate(xDiff, yDiff, zDiff);
    }

    public static double calculate(double xDiff, int yDiff, double zDiff) {
        double heuristic = 0;

        // if yDiff is 1 that means that currentY-goalY==1 which means that we're 1 block above where we should be
        // therefore going from 0,yDiff,0 to a GoalYLevel of 0 is accurate
        heuristic += GoalY.calculate(0, yDiff);

        //use the pythagorean and manhattan mixture from GoalXZ
        heuristic += GoalXZ.calculate(xDiff, zDiff);
        return heuristic;
    }

    @Override
    public BlockPos getGoalPos() {
        return new BlockPos(x, y, z);
    }
}
