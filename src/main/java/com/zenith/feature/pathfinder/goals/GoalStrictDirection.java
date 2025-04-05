package com.zenith.feature.pathfinder.goals;

import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.Direction;

/**
 * Dig a tunnel in a certain direction, but if you have to deviate from the path, go back to where you started
 */
public class GoalStrictDirection implements Goal {

    public final int x;
    public final int y;
    public final int z;
    public final int dx;
    public final int dz;

    public GoalStrictDirection(BlockPos origin, Direction direction) {
        x = origin.x();
        y = origin.y();
        z = origin.z();
        dx = direction.x();
        dz = direction.z();
        if (dx == 0 && dz == 0) {
            throw new IllegalArgumentException(direction + "");
        }
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return false;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        int distanceFromStartInDesiredDirection = (x - this.x) * dx + (z - this.z) * dz;

        int distanceFromStartInIncorrectDirection = Math.abs((x - this.x) * dz) + Math.abs((z - this.z) * dx);

        int verticalDistanceFromStart = Math.abs(y - this.y);

        // we want heuristic to decrease as desiredDirection increases
        double heuristic = -distanceFromStartInDesiredDirection * 100;

        heuristic += distanceFromStartInIncorrectDirection * 1000;
        heuristic += verticalDistanceFromStart * 1000;
        return heuristic;
    }

    @Override
    public double heuristic() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GoalStrictDirection goal = (GoalStrictDirection) o;
        return x == goal.x
                && y == goal.y
                && z == goal.z
                && dx == goal.dx
                && dz == goal.dz;
    }

    @Override
    public int hashCode() {
        int hash = (int) BlockPos.longHash(x, y, z);
        hash = hash * 630627507 + dx;
        hash = hash * -283028380 + dz;
        return hash;
    }

    @Override
    public String toString() {
        return String.format(
                "GoalStrictDirection{x=%s, y=%s, z=%s, dx=%s, dz=%s}",
                x,
                y,
                z,
                dx,
                dz
        );
    }
}
