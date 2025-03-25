package com.zenith.feature.pathfinder.goals;

import com.zenith.util.math.MathHelper;
import org.cloudburstmc.math.vector.Vector3d;

public record GoalXZ(int x, int z) implements Goal {
    private static final double SQRT_2 = Math.sqrt(2);

    @Override
    public boolean isInGoal(final int x, final int y, final int z) {
        return x == this.x && z == this.z;
    }

    @Override
    public double heuristic(final int x, final int y, final int z) {
        int xDiff = x - this.x;
        int zDiff = z - this.z;
        return calculate(xDiff, zDiff);
    }

    public static double calculate(double xDiff, double zDiff) {
        //This is a combination of pythagorean and manhattan distance
        //It takes into account the fact that pathing can either walk diagonally or forwards

        //It's not possible to walk forward 1 and right 2 in sqrt(5) time
        //It's really 1+sqrt(2) because it'll walk forward 1 then diagonally 1
        double x = Math.abs(xDiff);
        double z = Math.abs(zDiff);
        double straight;
        double diagonal;
        if (x < z) {
            straight = z - x;
            diagonal = x;
        } else {
            straight = x - z;
            diagonal = z;
        }
        diagonal *= SQRT_2;
        return (diagonal + straight) * 3.563f;
    }

    public static GoalXZ fromDirection(Vector3d origin, float yaw, double distance) {
        float theta = (float) Math.toRadians(yaw);
        double x = origin.getX() - Math.sin(theta) * distance;
        double z = origin.getZ() + Math.cos(theta) * distance;
        return new GoalXZ(MathHelper.floorI(x), MathHelper.floorI(z));
    }
}
