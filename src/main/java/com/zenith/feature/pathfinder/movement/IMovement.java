package com.zenith.feature.pathfinder.movement;

import com.zenith.mc.block.BlockPos;

public interface IMovement {
    double getCost();

    MovementStatus update();

    /**
     * Resets the current state status to {@link MovementStatus#PREPPING}
     */
    void reset();

    /**
     * Resets the cache for special break, place, and walk into blocks
     */
    void resetBlockCache();

    /**
     * @return Whether or not it is safe to cancel the current movement state
     */
    boolean safeToCancel();

    boolean calculatedWhileLoaded();

    BlockPos getSrc();

    BlockPos getDest();

    BlockPos getDirection();
}
