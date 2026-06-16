package com.aquarius.feature.pathfinder.movement.movements;

import com.google.common.collect.ImmutableSet;
import com.aquarius.feature.pathfinder.movement.*;
import com.aquarius.mc.block.BlockPos;
import com.aquarius.mc.block.BlockRegistry;
import lombok.ToString;

import java.util.Set;

import static com.aquarius.feature.pathfinder.movement.ActionCosts.*;

/**
 * Ride one block DOWN a downward bubble column (magma base, {@code drag=true}). No existing movement
 * descends through a water column ({@code MovementDownward} needs solid ground below, {@code
 * MovementFall} needs air), so this is the genuinely new primitive. The downward velocity itself is
 * applied by {@code Bot.tryCheckInsideBlocks}; this movement keeps the bot centered and, critically,
 * refuses to ride onto the magma block at the column's base — forcing an exit at an opening above it.
 */
@ToString(callSuper = true)
public class MovementBubbleDescend extends Movement {

    public MovementBubbleDescend(BlockPos start, BlockPos end) {
        super(start, end, new BlockPos[]{end});
    }

    @Override
    protected boolean autoJumpInLiquid() {
        // Never swim-up while riding a downward column: the +0.04/tick jump would fight the column's
        // pull and stall the descent.
        return false;
    }

    @Override
    public double calculateCost(CalculationContext context) {
        return cost(context, src.x(), src.y(), src.z());
    }

    @Override
    protected Set<BlockPos> calculateValidPositions() {
        return ImmutableSet.of(src, dest);
    }

    public static double cost(CalculationContext context, int x, int y, int z) {
        // Only valid while our feet are inside a downward bubble column — that's what pulls us down.
        if (!MovementHelper.isBubbleColumnDown(context.getId(x, y, z))) {
            return COST_INF;
        }
        // Only ride down through a continuous downward column.
        if (!MovementHelper.isBubbleColumnDown(context.getId(x, y - 1, z))) {
            return COST_INF;
        }
        // Never ride onto the magma block at the base of the column (contact damage). Stopping the
        // descent one block above the magma forces the planner to exit at a floor opening instead.
        if (context.getBlock(x, y - 2, z) == BlockRegistry.MAGMA_BLOCK) {
            return COST_INF;
        }
        return BUBBLE_COLUMN_DOWN_ONE_COST;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }
        // Reached (or, if the pull overshot, passed) the block below — done.
        if (ctx.playerFeet().y() <= dest.y()) {
            return state.setStatus(MovementStatus.SUCCESS);
        }
        // Got pushed back above the start: we lost the column (knockback / desync). Re-plan.
        if (ctx.playerFeet().y() > src.y()) {
            return state.setStatus(MovementStatus.UNREACHABLE);
        }
        // Stay centered on the shaft; the downward column physics carries us down.
        MovementHelper.moveToBlockCenter(state, ctx, src);
        return state;
    }
}
