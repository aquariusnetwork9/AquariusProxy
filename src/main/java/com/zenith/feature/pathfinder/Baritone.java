package com.zenith.feature.pathfinder;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.event.client.ClientBotTick;
import com.zenith.feature.pathfinder.behavior.InventoryBehavior;
import com.zenith.feature.pathfinder.behavior.LookBehavior;
import com.zenith.feature.pathfinder.behavior.PathingBehavior;
import com.zenith.feature.pathfinder.goals.Goal;
import com.zenith.feature.pathfinder.goals.GoalBlock;
import com.zenith.feature.pathfinder.goals.GoalXZ;
import com.zenith.feature.pathfinder.process.*;
import com.zenith.feature.player.InputRequest;
import com.zenith.mc.block.Block;
import com.zenith.util.math.MathHelper;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import lombok.Data;
import lombok.Getter;
import org.cloudburstmc.math.vector.Vector3d;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executors;
import java.util.function.Predicate;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

/**
 *
 * todo:
 *  doors, fences, and gates opening interactions
 *  interface for dispatching pathing commands with configurations
 *      i.e. disallow block breaking for certain goals, allow long distance falling, etc
 *  Rethink the baritone "Process" system. is there a better abstraction for multi-step goals?
 */

@Data
public class Baritone {
    public static final int MOVEMENT_PRIORITY = 200;
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
    private final Timer teleportDelayTimer = Timers.timer();
    private final IngamePathRenderer ingamePathRenderer = new IngamePathRenderer();

    public Baritone() {
        pathingControlManager.registerProcess(customGoalProcess);
        pathingControlManager.registerProcess(followProcess);
        pathingControlManager.registerProcess(getToBlockProcess);
        pathingControlManager.registerProcess(mineProcess);
        pathingControlManager.registerProcess(interactWithProcess);
        EVENT_BUS.subscribe(
            this,
            of(ClientBotTick.class, this::onClientBotTick),
            of(ClientBotTick.class, -40000, this::onClientBotTickPost),
            of(ClientBotTick.Starting.class, this::onClientBotTickStarting),
            of(ClientBotTick.Stopped.class, this::onClientBotTickStopped)
        );
    }

    public boolean isActive() {
        return getPathingBehavior().getGoal() != null;
    }

    public boolean isGoalActive(@NonNull Goal goal) {
        Goal activeGoal = getPathingBehavior().getGoal();
        return activeGoal != null && activeGoal.equals(goal);
    }

    public void pathTo(int x, int z) {
        pathTo(new GoalXZ(x, z));
    }

    public void pathTo(@NonNull GoalXZ goalXZ) {
        getCustomGoalProcess().setGoalAndPath(goalXZ);
    }

    public void pathTo(int x, int y, int z) {
        pathTo(new GoalBlock(x, y, z));
    }

    public void pathTo(@NonNull GoalBlock goalBlock) {
        getCustomGoalProcess().setGoalAndPath(goalBlock);
    }

    public void thisWay(final int dist) {
        Vector3d vector3d = MathHelper.calculateRayEndPos(
            CACHE.getPlayerCache().getX(),
            CACHE.getPlayerCache().getY(),
            CACHE.getPlayerCache().getZ(),
            CACHE.getPlayerCache().getYaw(),
            0,
            dist
        );
        pathTo(MathHelper.floorI(vector3d.getX()), MathHelper.floorI(vector3d.getZ()));
    }

    public void getTo(final Block block) {
        getGetToBlockProcess().getToBlock(block);
    }

    public void mine(Block... blocks) {
        getMineProcess().mine(blocks);
    }

    public void follow(Predicate<EntityLiving> entityPredicate) {
        getFollowProcess().follow(entityPredicate);
    }

    public void follow(EntityLiving target) {
        getFollowProcess().follow(target);
    }

    public void leftClickBlock(int x, int y, int z) {
        getInteractWithProcess().leftClickBlock(x, y, z);
    }

    public void rightClickBlock(int x, int y, int z) {
        getInteractWithProcess().rightClickBlock(x, y, z);
    }

    public void leftClickEntity(EntityLiving entity) {
        getInteractWithProcess().leftClickEntity(entity);
    }

    public void rightClickEntity(EntityLiving entity) {
        getInteractWithProcess().rightClickEntity(entity);
    }

    public void goal(@NonNull Goal goal) {
        getCustomGoalProcess().setGoalAndPath(goal);
    }

    public void stop() {
        getPathingBehavior().cancelEverything();
    }

    public @Nullable Goal currentGoal() {
        return pathingBehavior.getGoal();
    }

    public void onClientBotTick(ClientBotTick event) {
        if (!CACHE.getPlayerCache().isAlive()) return;
        if (CACHE.getChunkCache().getCache().size() < 8) return;
        if (!teleportDelayTimer.tick(CONFIG.client.extra.pathfinder.teleportDelayMs, false)) return;
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
                .input(inputOverrideHandler.currentInput)
                .priority(MOVEMENT_PRIORITY);
            if (rotation != null) {
                req
                    .yaw(rotation.yaw())
                    .pitch(rotation.pitch());
            }
            INPUTS.submit(req.build());
        }
    }

    public void onClientBotTickPost(ClientBotTick event) {
        pathingControlManager.postTick();
    }

    public void onClientBotTickStopped(ClientBotTick.Stopped event) {
        getPathingBehavior().cancelEverything();
    }

    public void onClientBotTickStarting(ClientBotTick.Starting event) {
        getPathingBehavior().cancelEverything();
    }

    public void onPlayerPosRotate() {
        teleportDelayTimer.reset();
    }
}
