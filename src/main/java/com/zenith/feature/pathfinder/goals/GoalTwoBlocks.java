package com.zenith.feature.pathfinder.goals;

import com.zenith.mc.block.BlockPos;

/**
 * Useful if the goal is just to mine a block. This goal will be satisfied if the specified
 * {@link BlockPos} is at to or above the specified position for this goal.
 *
 * @param x The X block position of this goal
 * @param y The Y block position of this goal
 * @param z The Z block position of this goal
 * @author leijurv
 */

public record GoalTwoBlocks(int x, int y, int z) implements Goal, PosGoal {
     public GoalTwoBlocks(BlockPos pos) {
        this(pos.x(), pos.y(), pos.z());
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return x == this.x && (y == this.y || y == this.y - 1) && z == this.z;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        int xDiff = x - this.x;
        int yDiff = y - this.y;
        int zDiff = z - this.z;
        return GoalBlock.calculate(xDiff, yDiff < 0 ? yDiff + 1 : yDiff, zDiff);
    }

//    @Override
//    public BlockPos getGoalPos() {
//        return new BlockPos(x, y, z);
//    }

//    @Override
//    public boolean equals(Object o) {
//        if (this == o) {
//            return true;
//        }
//        if (o == null || getClass() != o.getClass()) {
//            return false;
//        }
//
//        GoalTwoBlocks goal = (GoalTwoBlocks) o;
//        return x == goal.x
//                && y == goal.y
//                && z == goal.z;
//    }
//
    @Override
    public int hashCode() {
        return (int) BlockPos.longHash(x, y, z) * 516508351;
    }

    @Override
    public String toString() {
        return String.format(
                "GoalTwoBlocks{x=%s,y=%s,z=%s}",
                x,
                y,
                z
        );
    }

    @Override
    public BlockPos getGoalPos() {
        return new BlockPos(x, y, z);
    }
}
