package com.aquarius.feature.pathfinder;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.aquarius.cache.data.entity.EntityLiving;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.pathfinder.behavior.InventoryBehavior;
import com.aquarius.feature.pathfinder.behavior.LookBehavior;
import com.aquarius.feature.pathfinder.behavior.PathingBehavior;
import com.aquarius.feature.pathfinder.goals.Goal;
import com.aquarius.feature.pathfinder.goals.GoalBlock;
import com.aquarius.feature.pathfinder.goals.GoalXZ;
import com.aquarius.feature.pathfinder.process.*;
import com.aquarius.feature.player.InputRequest;
import com.aquarius.mc.block.Block;
import com.aquarius.mc.block.BlockPos;
import com.aquarius.mc.item.ItemData;
import com.aquarius.util.math.MathHelper;
import com.aquarius.util.timer.Timer;
import com.aquarius.util.timer.Timers;
import lombok.Data;
import lombok.Getter;
import org.cloudburstmc.math.vector.Vector3d;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.*;

/**
 *
 * todo:
 *  doors, fences, and gates opening interactions
 *  interface for dispatching pathing commands with configurations
 *      i.e. disallow block breaking for certain goals, allow long distance falling, etc
 *  Rethink the baritone "Process" system. is there a better abstraction for multi-step goals?
 */

@Data
public class Baritone implements Pathfinder {
    public static final int POST_TICK_PRIORITY = -40000;
    private final PathingBehavior pathingBehavior = new PathingBehavior(this);
    private final InputOverrideHandler inputOverrideHandler = new InputOverrideHandler(this);
    private final LookBehavior lookBehavior = new LookBehavior(this);
    private final InventoryBehavior inventoryBehavior = new InventoryBehavior(this);
    private final PlayerContext playerContext = PlayerContext.INSTANCE;
    @Getter private static final ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder()
            .setNameFormat("Baritone")
            .setDaemon(true)
            .setUncaughtExceptionHandler((t, e) -> PATH_LOG.error("Error in Baritone thread", e))
            .build()));
    private final PathingControlManager pathingControlManager = new PathingControlManager(this);
    private final CustomGoalProcess customGoalProcess = new CustomGoalProcess(this);
    private final FollowProcess followProcess = new FollowProcess(this);
    private final GetToBlockProcess getToBlockProcess = new GetToBlockProcess(this);
    private final MineProcess mineProcess = new MineProcess(this);
    private final InteractWithProcess interactWithProcess = new InteractWithProcess(this);
    private final ClearAreaProcess clearAreaProcess = new ClearAreaProcess(this);
    private final Timer teleportDelayTimer = Timers.timer();
    private final Timer teleportStarvationTimer = Timers.timer();
    private double lastTeleportX = Double.NaN;
    private double lastTeleportY = Double.NaN;
    private double lastTeleportZ = Double.NaN;
    private static final double TELEPORT_DEDUP_EPSILON = 0.05;    // blocks; a resend within this of the last correction isn't a NEW one
    private static final long TELEPORT_STARVATION_CAP_MS = 3000L; // never withhold a tick longer than this no matter how dense the corrections
    private final IngamePathRenderer ingamePathRenderer = new IngamePathRenderer();

    public Baritone() {
        pathingControlManager.registerProcess(customGoalProcess);
        pathingControlManager.registerProcess(followProcess);
        pathingControlManager.registerProcess(getToBlockProcess);
        pathingControlManager.registerProcess(mineProcess);
        pathingControlManager.registerProcess(interactWithProcess);
        pathingControlManager.registerProcess(clearAreaProcess);
        EVENT_BUS.subscribe(
            this,
            of(ClientBotTick.class, this::onClientBotTick),
            of(ClientBotTick.class, POST_TICK_PRIORITY, this::onClientBotTickPost),
            of(ClientBotTick.Starting.class, this::onClientBotTickStarting),
            of(ClientBotTick.Stopped.class, this::onClientBotTickStopped)
        );
    }

    public static int getPriority() {
        return Objects.requireNonNullElse(CONFIG.client.extra.pathfinder.priority, 7000);
    }

    public boolean isActive() {
        return getPathingBehavior().getGoal() != null || getPathingControlManager().isActive();
    }

    public boolean isGoalActive(@NonNull Goal goal) {
        Goal activeGoal = getPathingBehavior().getGoal();
        return activeGoal != null && activeGoal.equals(goal);
    }

    @Override
    public PathingRequestFuture pathTo(int x, int z) {
        return pathTo(new GoalXZ(x, z));
    }

    @Override
    public PathingRequestFuture pathTo(int x, int y, int z) {
        return pathTo(new GoalBlock(x, y, z));
    }

    @Override
    public PathingRequestFuture pathTo(@NonNull Goal goal) {
        return getCustomGoalProcess().setGoalAndPath(goal);
    }

    @Override
    public PathingRequestFuture thisWay(final int dist) {
        Vector3d vector3d = MathHelper.calculateRayEndPos(
            CACHE.getPlayerCache().getX(),
            CACHE.getPlayerCache().getY(),
            CACHE.getPlayerCache().getZ(),
            CACHE.getPlayerCache().getYaw(),
            0,
            dist
        );
        return pathTo(MathHelper.floorI(vector3d.getX()), MathHelper.floorI(vector3d.getZ()));
    }

    @Override
    public PathingRequestFuture getTo(final Block block) {
        return getGetToBlockProcess().getToBlock(block);
    }

    @Override
    public PathingRequestFuture getTo(final Block block, boolean rightClickContainerOnArrival) {
        return getGetToBlockProcess().getToBlock(block, rightClickContainerOnArrival);
    }

    @Override
    public PathingRequestFuture mine(Block... blocks) {
        return getMineProcess().mine(blocks);
    }

    @Override
    public PathingRequestFuture follow(Predicate<EntityLiving> entityPredicate) {
        return getFollowProcess().follow(entityPredicate);
    }

    @Override
    public PathingRequestFuture follow(EntityLiving target) {
        return getFollowProcess().follow(target);
    }

    @Override
    public PathingRequestFuture pickup(final ItemData... items) {
        return getFollowProcess().pickup(items);
    }

    @Override
    public PathingRequestFuture pickup() {
        return getFollowProcess().pickup();
    }

    @Override
    public PathingRequestFuture leftClickBlock(int x, int y, int z) {
        return getInteractWithProcess().leftClickBlock(x, y, z);
    }

    @Override
    public PathingRequestFuture rightClickBlock(int x, int y, int z) {
        return getInteractWithProcess().rightClickBlock(x, y, z);
    }

    @Override
    public PathingRequestFuture breakBlock(int x, int y, int z, boolean autoTool) {
        return getInteractWithProcess().breakBlock(x, y, z, autoTool);
    }

    /**
     * API may change or be removed in future updates
     */
    @ApiStatus.Experimental
    @Override
    public PathingRequestFuture placeBlock(int x, int y, int z, ItemData placeItem) {
        return getInteractWithProcess().placeBlock(x, y, z, placeItem);
    }

    @Override
    public PathingRequestFuture leftClickEntity(EntityLiving entity) {
        return getInteractWithProcess().leftClickEntity(entity);
    }

    @Override
    public PathingRequestFuture rightClickEntity(EntityLiving entity) {
        return getInteractWithProcess().rightClickEntity(entity);
    }

    @Override
    public PathingRequestFuture clearArea(BlockPos pos1, BlockPos pos2) {
        return getClearAreaProcess().clearArea(pos1, pos2);
    }

    @Override
    public void stop() {
        getPathingBehavior().cancelEverything();
    }

    @Override
    public @Nullable Goal currentGoal() {
        return pathingBehavior.getGoal();
    }

    private void onClientBotTick(ClientBotTick event) {
        if (!CACHE.getPlayerCache().isAlive()) return;
        if (CACHE.getChunkCache().getCache().size() < 8) return;
        if (!teleportGateOpen()) return;
        lookBehavior.onTick();
        pathingBehavior.onTick();
        if (pathingControlManager.isActive()) {
            inventoryBehavior.onTick();
        }
        inputOverrideHandler.onTick();
        ingamePathRenderer.onTick();

        if (pathingBehavior.isPathing() || (pathingControlManager.isActive() && lookBehavior.currentRotation != null)) {
            var rotation = lookBehavior.currentRotation;
            var req = InputRequest.builder()
                .owner(this)
                .input(inputOverrideHandler.currentInput)
                .priority(getPriority());
            if (rotation != null) {
                req
                    .yaw(rotation.yaw())
                    .pitch(rotation.pitch());
            }
            INPUTS.submit(req.build());
        }
    }

    private void onClientBotTickPost(ClientBotTick event) {
        pathingControlManager.postTick();
    }

    private void onClientBotTickStopped(ClientBotTick.Stopped event) {
        getPathingBehavior().cancelEverything();
    }

    private void onClientBotTickStarting(ClientBotTick.Starting event) {
        getPathingBehavior().cancelEverything();
    }

    public void onPlayerPosRotate() {
        var pc = CACHE.getPlayerCache();
        double x = pc.getX(), y = pc.getY(), z = pc.getZ();
        boolean sameAsLast = !Double.isNaN(lastTeleportX)
            && Math.abs(x - lastTeleportX) < TELEPORT_DEDUP_EPSILON
            && Math.abs(y - lastTeleportY) < TELEPORT_DEDUP_EPSILON
            && Math.abs(z - lastTeleportZ) < TELEPORT_DEDUP_EPSILON;
        lastTeleportX = x;
        lastTeleportY = y;
        lastTeleportZ = z;
        // A resend of essentially the same position isn't a new correction to settle for. A dense, unbroken stream of
        // these (observed live: identical coords resent 2-3x/sec, indefinitely) used to keep re-arming this timer
        // before it could ever elapse, permanently starving onClientBotTick() of ticks -- so Baritone could compute a
        // path but never actually drive it, deadlocked forever even once the position had genuinely settled.
        if (!sameAsLast) teleportDelayTimer.reset();
    }

    /**
     * True once it's safe for {@link #onClientBotTick} to actually tick (and therefore move the bot): either the
     * normal per-teleport cooldown has elapsed, or -- regardless of how densely corrections keep arriving --
     * {@link #TELEPORT_STARVATION_CAP_MS} has passed since the last tick we were allowed to take. Baritone's whole
     * purpose here is often to move the bot OUT of a stuck situation, so a resend storm must never be able to
     * withhold ticking from it indefinitely.
     */
    private boolean teleportGateOpen() {
        if (teleportDelayTimer.tick(CONFIG.client.extra.pathfinder.teleportDelayMs, false)) {
            teleportStarvationTimer.reset();
            return true;
        }
        if (teleportStarvationTimer.tick(TELEPORT_STARVATION_CAP_MS, false)) {
            teleportDelayTimer.reset();
            teleportStarvationTimer.reset();
            return true;
        }
        return false;
    }
}
