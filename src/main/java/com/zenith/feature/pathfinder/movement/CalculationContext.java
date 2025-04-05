package com.zenith.feature.pathfinder.movement;

import com.zenith.feature.pathfinder.Baritone;
import com.zenith.feature.pathfinder.BlockStateInterface;
import com.zenith.feature.pathfinder.PrecomputedData;
import com.zenith.feature.pathfinder.goals.Goal;
import com.zenith.feature.pathfinder.util.ToolSet;
import com.zenith.feature.world.World;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockPos;
import org.jspecify.annotations.Nullable;

import static com.zenith.Shared.CACHE;
import static com.zenith.Shared.CONFIG;
import static com.zenith.feature.pathfinder.movement.ActionCosts.COST_INF;

public class CalculationContext {

//    public final boolean safeForThreadedUse;
//    public final IBaritone baritone;
//    public final Level world;
//    public final WorldData worldData;
    public final ToolSet toolSet = new ToolSet();
    public final boolean hasThrowaway;
//        this.toolSet = new ToolSet(player);
//        this.hasWaterBucket = Baritone.settings().allowWaterBucketFall.value && Inventory.isHotbarSlot(player.getInventory().findSlotMatchingItem(STACK_BUCKET_WATER)) && world.dimension() != Level.NETHER;
// Baritone.settings().allowSprint.value && player.getFoodData().getFoodLevel() > 6;
    //    public final boolean hasWaterBucket;
//    public final boolean hasThrowaway;
    public final boolean canSprint = CONFIG.client.extra.pathfinder.allowSprint && CACHE.getPlayerCache().getThePlayer().getFood() > 6;
    protected final double placeBlockCost = 20; // protected because you should call the function instead
// Baritone.settings().allowBreak.value;
    //    protected final double placeBlockCost; // protected because you should call the function instead
    public final boolean allowBreak = CONFIG.client.extra.pathfinder.allowBreak;
//    public final List<Block> allowBreakAnyway;
    public final boolean allowParkour = CONFIG.client.extra.pathfinder.allowParkour;
    public final boolean allowParkourPlace = CONFIG.client.extra.pathfinder.allowParkourPlace;
//    public final boolean allowJumpAt256;
    public final boolean allowParkourAscend = CONFIG.client.extra.pathfinder.allowParkourAscend;
    public final boolean assumeWalkOnWater;
//    public boolean allowFallIntoLava;
    public final int frostWalker = 0;
    public final boolean allowDiagonalDescend = CONFIG.client.extra.pathfinder.allowDiagonalDescend;
    public final boolean allowDiagonalAscend = CONFIG.client.extra.pathfinder.allowDiagonalAscend;
    public final boolean allowDownward = CONFIG.client.extra.pathfinder.allowDownward;
    public int minFallHeight = 3;
    public int maxFallHeightNoWater = CONFIG.client.extra.pathfinder.maxFallHeightNoWater;
    public boolean allowLongFall = CONFIG.client.extra.pathfinder.allowLongFall;
    public double longFallCostLogMultiplier = CONFIG.client.extra.pathfinder.longFallCostLogMultiplier;
    public double longFallCostAddCost = CONFIG.client.extra.pathfinder.longFallCostAddCost;
//    public final int maxFallHeightBucket;
    public final double waterWalkSpeed;
    public final double breakBlockAdditionalCost = CONFIG.client.extra.pathfinder.blockBreakAdditionalCost;
    public double backtrackCostFavoringCoefficient = 0.5;
    public double jumpPenalty = 2;
    public final double walkOnWaterOnePenalty;
//    public final BetterWorldBorder worldBorder;

    public final PrecomputedData precomputedData = PrecomputedData.INSTANCE;
    @Nullable public final Goal goal;

    public CalculationContext(Goal goal) {
        this.goal = goal;
        this.hasThrowaway = CONFIG.client.extra.pathfinder.allowPlace && Baritone.INSTANCE.getInventoryBehavior().hasGenericThrowaway();
        this.assumeWalkOnWater = false; // Baritone.settings().assumeWalkOnWater.value;
        float waterSpeedMultiplier = 1.0f;
        this.waterWalkSpeed = ActionCosts.WALK_ONE_IN_WATER_COST + ActionCosts.WALK_ONE_BLOCK_COST * waterSpeedMultiplier;
        this.walkOnWaterOnePenalty = 3;
    }
    public CalculationContext() {
        this(null);
    }

    public double costOfPlacingAt(CalculationContext context, int x, int y, int z, int current) {
        if (!hasThrowaway) { // only true if allowPlace is true, see constructor
            return COST_INF;
        }
        if (context.goal != null && context.goal.isInGoal(x, y, z)) {
            return 0;
        }
        return placeBlockCost;
    }

    public double breakCostMultiplierAt(int x, int y, int z, int current) {
        if (!allowBreak) {
            return COST_INF;
        }
        return 1;
    }

    public boolean isLoaded(final int x, final int z) {
        return BlockStateInterface.isLoaded(x, z);
    }

//    public BlockState get(final BlockPos pos) {
//        return get(pos.x(), pos.y(), pos.z());
//    }

    public int getId(final BlockPos pos) {
        return getId(pos.x(), pos.y(), pos.z());
    }

//    public BlockState get(int x, int y, int z) {
//        return World.getBlockState(x, y, z);
//    }

    public Block getBlock(int x, int y, int z) {
        return World.getBlock(x, y, z);
    }

    public int getId(int x, int y, int z) {
        return World.getBlockStateId(x, y, z);
    }
}
