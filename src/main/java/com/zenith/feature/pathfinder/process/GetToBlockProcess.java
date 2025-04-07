package com.zenith.feature.pathfinder.process;

import com.google.common.util.concurrent.ListenableFuture;
import com.zenith.feature.pathfinder.*;
import com.zenith.feature.pathfinder.goals.*;
import com.zenith.feature.pathfinder.movement.MovementHelper;
import com.zenith.feature.pathfinder.util.BlockOptionalMetaLookup;
import com.zenith.feature.pathfinder.util.RotationUtils;
import com.zenith.feature.pathfinder.util.WorldScanner;
import com.zenith.feature.world.Rotation;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockRegistry;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static com.zenith.Globals.*;

public final class GetToBlockProcess extends BaritoneProcessHelper {

    private Block gettingTo;
    private List<BlockPos> knownLocations;
    private List<BlockPos> blacklist; // locations we failed to calc to
    private BlockPos start;
    @Nullable ListenableFuture<?> scanFuture;

    private int tickCount = 0;
    private int arrivalTickCount = 0;

    public GetToBlockProcess(Baritone baritone) {
        super(baritone);
    }

    public void getToBlock(Block block) {
        onLostControl();
        gettingTo = block;
        start = ctx.playerFeet();
        blacklist = new ArrayList<>();
        arrivalTickCount = 0;
        rescan();
    }

    @Override
    public boolean isActive() {
        return gettingTo != null;
    }

    @Override
    public synchronized PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel, final Goal currentGoal, final PathingCommand prevCommand) {
        if (knownLocations == null) {
            rescan();
        }
        if (knownLocations.isEmpty()) {
            if (CONFIG.client.extra.pathfinder.getToBlockExploreForBlocks) {
                if (!(currentGoal instanceof GoalRunAway)) {
                    PATH_LOG.info("Exploring to find {}", gettingTo);
                } else {
                    if (calcFailed) {
                        PATH_LOG.info("Unable to find GetToBlock explore path, stopping ");
                        if (isSafeToCancel) {
                            onLostControl();
                        }
                        return new PathingCommand(null, PathingCommandType.DEFER);
                    }
                }
                int mineGoalUpdateInterval = 50; // Baritone.settings().mineGoalUpdateInterval.value;
                if (tickCount++ % mineGoalUpdateInterval == 0 && (scanFuture == null || scanFuture.isDone())) { // big brain
                    scanFuture = Baritone.getExecutor().submit(this::rescan);
                }
                return new PathingCommand(new GoalRunAway(1, start) {
                    @Override
                    public boolean isInGoal(int x, int y, int z) {
                        return false;
                    }

                    @Override
                    public double heuristic() {
                        return Double.NEGATIVE_INFINITY;
                    }
                }, PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH);
            }
            PATH_LOG.info("No known locations of " + gettingTo + ", canceling GetToBlock");
            if (isSafeToCancel) {
                onLostControl();
            }
            return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
        }
        Goal goal = new GoalComposite(knownLocations.stream().map(this::createGoal).toArray(Goal[]::new));
        if (calcFailed) {
            if (CONFIG.client.extra.pathfinder.getToBlockBlacklistClosestOnFailure) {
//                PATH_LOG.info("Unable to find any path to " + gettingTo + ", blacklisting presumably unreachable closest instances...");
                blacklistClosest();
                return new PathingCommand(null, PathingCommandType.DEFER);
            } else {
                PATH_LOG.info("Unable to find any path to " + gettingTo + ", canceling GetToBlock");
                if (isSafeToCancel) {
                    onLostControl();
                }
                return new PathingCommand(goal, PathingCommandType.CANCEL_AND_SET_GOAL);
            }
        }
        int mineGoalUpdateInterval = 50; // Baritone.settings().mineGoalUpdateInterval.value;
        if (tickCount++ % mineGoalUpdateInterval == 0 && (scanFuture == null || scanFuture.isDone())) { // big brain
            scanFuture = Baritone.getExecutor().submit(this::rescan);
        }
        if (goal.isInGoal(ctx.playerFeet()) && goal.isInGoal(baritone.getPathingBehavior().pathStart()) && isSafeToCancel) {
            // we're there
            if (rightClickOnArrival(gettingTo)) {
                if (rightClick()) {
                    onLostControl();
                    return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
                }
            } else {
                onLostControl();
                return new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL);
            }
        }
        return new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
    }

    // blacklist the closest block and its adjacent blocks
    public synchronized boolean blacklistClosest() {
        List<BlockPos> newBlacklist = new ArrayList<>();
        knownLocations.stream().min(Comparator.comparingDouble(ctx.playerFeet()::distance)).ifPresent(newBlacklist::add);
        outer:
        while (true) {
            for (BlockPos known : knownLocations) {
                for (BlockPos blacklist : newBlacklist) {
                    if (areAdjacent(known, blacklist)) { // directly adjacent
                        newBlacklist.add(known);
                        knownLocations.remove(known);
                        continue outer;
                    }
                }
            }
            break;
        }
        PATH_LOG.info("Blacklisting unreachable locations " + newBlacklist);
        blacklist.addAll(newBlacklist);
        return !newBlacklist.isEmpty();
    }

    // safer than direct double comparison from distanceSq
    private boolean areAdjacent(BlockPos posA, BlockPos posB) {
        int diffX = Math.abs(posA.x() - posB.x());
        int diffY = Math.abs(posA.y() - posB.y());
        int diffZ = Math.abs(posA.z() - posB.z());
        return (diffX + diffY + diffZ) == 1;
    }

    @Override
    public synchronized void onLostControl() {
        gettingTo = null;
        knownLocations = null;
        start = null;
        blacklist = null;
        baritone.getInputOverrideHandler().clearAllKeys();
    }

    @Override
    public String displayName0() {
        if (knownLocations.isEmpty()) {
            return "Exploring randomly to find " + gettingTo + ", no known locations";
        }
        return "Get To " + gettingTo + ", " + knownLocations.size() + " known locations";
    }

    private synchronized void rescan() {
        if (gettingTo == null) {
            knownLocations = Collections.emptyList();
            return;
        }
        List<BlockPos> positions = WorldScanner.scanChunkRadius(new BlockOptionalMetaLookup(gettingTo), 64, -64, 32);
        positions.removeIf(blacklist::contains);
        knownLocations = positions;
    }

    private Goal createGoal(BlockPos pos) {
        if (walkIntoInsteadOfAdjacent(gettingTo)) {
            return new GoalBlock(pos);
        }
        if (blockOnTopMustBeRemoved(gettingTo) && MovementHelper.isBlockNormalCube(BlockStateInterface.getId(pos.above()))) { // TODO this should be the check for chest openability
            return new GoalBlock(pos.above());
        }
        return new GoalGetToBlock(pos);
    }

    private boolean rightClick() {
        for (BlockPos pos : knownLocations) {
            Optional<Rotation> reachable = RotationUtils.reachable(ctx, pos, ctx.player().getBlockReachDistance());
            if (reachable.isPresent()) {
                baritone.getLookBehavior().updateRotation(reachable.get());
                if (knownLocations.contains(ctx.getSelectedBlock().orElse(null))) {
                    baritone.getInputOverrideHandler().setInputForceState(PathInput.RIGHT_CLICK_BLOCK, true); // TODO find some way to right click even if we're in an ESC menu
                    if (CACHE.getPlayerCache().getInventoryCache().getOpenContainerId() != 0) {
                        return true;
                    }
                }
                if (arrivalTickCount++ > 20) {
                    PATH_LOG.info("Right click timed out");
                    return true;
                }
                return false; // trying to right click, will do it next tick or so
            }
        }
        PATH_LOG.info("Arrived but failed to right click open");
        return true;
    }

    private boolean walkIntoInsteadOfAdjacent(Block block) {
//        if (!Baritone.settings().enterPortal.value) {
//            return false;
//        }
        return block == BlockRegistry.NETHER_PORTAL;
    }

    private boolean rightClickOnArrival(Block block) {
//        if (!Baritone.settings().rightClickContainerOnArrival.value) {
//            return false;
//        }
        return block == BlockRegistry.CRAFTING_TABLE || block == BlockRegistry.FURNACE || block == BlockRegistry.BLAST_FURNACE || block == BlockRegistry.ENDER_CHEST || block == BlockRegistry.CHEST || block == BlockRegistry.TRAPPED_CHEST;
    }

    private boolean blockOnTopMustBeRemoved(Block block) {
        if (!rightClickOnArrival(block)) { // only if we plan to actually open it on arrival
            return false;
        }
        // only these chests; you can open a crafting table or furnace even with a block on top
        return block == BlockRegistry.ENDER_CHEST || block == BlockRegistry.CHEST || block == BlockRegistry.TRAPPED_CHEST;
    }
}
