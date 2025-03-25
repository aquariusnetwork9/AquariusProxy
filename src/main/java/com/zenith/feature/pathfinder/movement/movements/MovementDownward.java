package com.zenith.feature.pathfinder.movement.movements;

import com.google.common.collect.ImmutableSet;
import com.zenith.feature.pathfinder.BlockStateInterface;
import com.zenith.feature.pathfinder.movement.*;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockTags;

import java.util.Set;

import static com.zenith.feature.pathfinder.movement.ActionCosts.*;

public class MovementDownward extends Movement {

    private int numTicks = 0;

    public MovementDownward(BlockPos start, BlockPos end) {
        super(start, end, new BlockPos[]{end});
    }

    @Override
    public void reset() {
        super.reset();
        numTicks = 0;
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
        if (!context.allowDownward) {
            return COST_INF;
        }
        if (!MovementHelper.canWalkOn(context, x, y - 2, z)) {
            return COST_INF;
        }
        int down = context.getId(x, y - 1, z);
        Block downBlock = BlockStateInterface.getBlock(down);
        if (downBlock.blockTags().contains(BlockTags.CLIMBABLE)) {
            return LADDER_DOWN_ONE_COST;
        } else {
            // we're standing on it, while it might be block falling, it'll be air by the time we get here in the movement
            return FALL_N_BLOCKS_COST[1] + MovementHelper.getMiningDurationTicks(context, x, y - 1, z, down, false);
        }
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) {
            return state;
        }

        if (ctx.playerFeet().equals(dest)) {
            return state.setStatus(MovementStatus.SUCCESS);
        } else if (!playerInValidPosition()) {
            return state.setStatus(MovementStatus.UNREACHABLE);
        }
        double diffX = ctx.player().getX() - (dest.x() + 0.5);
        double diffZ = ctx.player().getZ() - (dest.z() + 0.5);
        double ab = Math.sqrt(diffX * diffX + diffZ * diffZ);

        Block downBlock = BlockStateInterface.getBlock(src);
        if (downBlock.blockTags().contains(BlockTags.CLIMBABLE)) {
            MovementHelper.moveToBlockCenter(state, ctx, src);
            return state;
        }
        if (numTicks++ < 10 && ab < 0.2) {
            return state;
        }
        MovementHelper.moveTowards(state, dest);
        return state;
    }
}
