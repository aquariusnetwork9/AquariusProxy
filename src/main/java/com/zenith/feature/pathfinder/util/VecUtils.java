package com.zenith.feature.pathfinder.util;

import com.zenith.cache.data.entity.Entity;
import com.zenith.feature.pathfinder.BlockStateInterface;
import com.zenith.feature.world.PlayerSimulation;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockRegistry;
import org.cloudburstmc.math.vector.Vector3d;

import static com.zenith.Globals.BLOCK_DATA;

public class VecUtils {
    public static Vector3d calculateBlockCenter(BlockPos pos) {
        int state = BlockStateInterface.getId(pos);
        var interactionBoxes = BLOCK_DATA.getInteractionBoxesFromBlockStateId(state);
        if (interactionBoxes.isEmpty()) {
            return getBlockPosCenter(pos);
        }
        var block = BlockStateInterface.getBlock(state);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        double maxZ = Double.MIN_VALUE;
        for (int i = 0; i < interactionBoxes.size(); i++) {
            var box = interactionBoxes.get(i);
            minX = Math.min(minX, box.minX());
            minY = Math.min(minY, box.minY());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY());
            maxZ = Math.max(maxZ, box.maxZ());
        }
        double xDiff = (minX + maxX) / 2;
        double yDiff = (minY + maxY) / 2;
        double zDiff = (minZ + maxZ) / 2;
        if (Double.isNaN(xDiff) || Double.isNaN(yDiff) || Double.isNaN(zDiff)) {
            throw new IllegalStateException(block + " " + pos + " " + interactionBoxes);
        }
        if (block == BlockRegistry.FIRE) {//look at bottom of fire when putting it out
            yDiff = 0;
        }
        return Vector3d.from(pos.x() + xDiff, pos.y() + yDiff, pos.z() + zDiff);
    }

    public static Vector3d getBlockPosCenter(BlockPos pos) {
        return Vector3d.from(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
    }

    /**
     * Gets the distance from the specified position to the assumed center of the specified block position.
     *
     * @param pos The block position
     * @param x   The x pos
     * @param y   The y pos
     * @param z   The z pos
     * @return The distance from the assumed block center to the position
     * @see #getBlockPosCenter(BlockPos)
     */
    public static double distanceToCenter(BlockPos pos, double x, double y, double z) {
        double xdiff = pos.x() + 0.5 - x;
        double ydiff = pos.y() + 0.5 - y;
        double zdiff = pos.z() + 0.5 - z;
        return Math.sqrt(xdiff * xdiff + ydiff * ydiff + zdiff * zdiff);
    }


    public static double entityDistanceToCenter(final Entity entity, final BlockPos pos) {
        return distanceToCenter(pos, entity.getX(), entity.getY(), entity.getZ());
    }

    public static double entityDistanceToCenter(final PlayerSimulation player, final BlockPos pos) {
        return distanceToCenter(pos, player.getX(), player.getY(), player.getZ());
    }

    public static double entityFlatDistanceToCenter(final PlayerSimulation player, final BlockPos pos) {
        return distanceToCenter(pos, player.getX(), pos.y() + 0.5, player.getZ());
    }
}
