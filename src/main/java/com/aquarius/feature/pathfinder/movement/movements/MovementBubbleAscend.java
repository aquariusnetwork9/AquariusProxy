package com.aquarius.feature.pathfinder.movement.movements;

import com.google.common.collect.ImmutableSet;
import com.aquarius.feature.pathfinder.movement.*;
import com.aquarius.mc.block.BlockPos;
import lombok.ToString;

import java.util.Set;

import static com.aquarius.feature.pathfinder.movement.ActionCosts.*;

/**
 * Ride one block UP an upward bubble column (soul sand base, {@code drag=false}). The actual upward
 * velocity is applied by the core movement sim in {@code Bot.tryCheckInsideBlocks}; this movement
 * just keeps the bot centered in the shaft and reports completion. The planner chains these to climb
 * the shaft and exits laterally with a normal {@link MovementTraverse} at whatever floor has an
 * opening, so floor detection falls out of the search.
 */
@ToString(callSuper = true)
public class MovementBubbleAscend extends Movement {

    public MovementBubbleAscend(BlockPos start, BlockPos end) {
        super(start, end, new BlockPos[]{start.above(2)});
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
        // Only valid while our feet are inside an upward bubble column — that's what pushes us up.
        if (!MovementHelper.isBubbleColumnUp(context.getId(x, y, z))) {
            return COST_INF;
        }
        // We float up into the block above our feet; it must be passable (more column, water, or air
        // at the surface). We never mine to ascend a column in v1.
        if (!MovementHelper.canWalkThrough(context, x, y + 1, z, context.getId(x, y + 1, z))) {
            return COST_INF;
        }
        // Head clearance after rising (our hitbox is 2 tall).
        if (!MovementHelper.canWalkThrough(context, x, y + 2, z, context.getId(x, y + 2, z))) {
            return COST_INF;
        }
        return BUBBLE_COLUMN_UP_ONE_COST;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }
        // Reached (or, if the surge overshot, passed) the block above — done; the next move takes over.
        if (ctx.playerFeet().y() >= dest.y()) {
            return state.setStatus(MovementStatus.SUCCESS);
        }
        // Fell below the start: we lost the column (knockback / desync). Re-plan.
        if (ctx.playerFeet().y() < src.y()) {
            return state.setStatus(MovementStatus.UNREACHABLE);
        }
        // Stay centered on the shaft. The column physics (plus the base auto-jump-in-liquid) lifts us;
        // we only steer so the rise stays inside the shaft.
        MovementHelper.moveToBlockCenter(state, ctx, src);
        return state;
    }
}
