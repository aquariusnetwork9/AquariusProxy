package com.zenith.feature.pathfinder;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.zenith.Proxy;
import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.event.module.ClientBotTick;
import com.zenith.feature.pathfinder.behavior.InventoryBehavior;
import com.zenith.feature.pathfinder.behavior.LookBehavior;
import com.zenith.feature.pathfinder.behavior.PathingBehavior;
import com.zenith.feature.pathfinder.calc.IPath;
import com.zenith.feature.pathfinder.goals.Goal;
import com.zenith.feature.pathfinder.goals.GoalBlock;
import com.zenith.feature.pathfinder.goals.GoalXZ;
import com.zenith.feature.pathfinder.process.*;
import com.zenith.feature.world.InputRequest;
import com.zenith.feature.world.Rotation;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockPos;
import com.zenith.util.Timer;
import com.zenith.util.Timers;
import com.zenith.util.math.MathHelper;
import lombok.Data;
import lombok.Getter;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.Particle;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.ParticleType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelParticlesPacket;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Shared.*;

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
    public static final Baritone INSTANCE = new Baritone();
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
    private Timer teleportDelayTimer = Timers.timer();

    private Baritone() {
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

        if (pathingBehavior.isPathing() && CONFIG.client.extra.pathfinder.renderPath) {
            if (renderPathTimer.tick(CONFIG.client.extra.pathfinder.pathRenderIntervalTicks)) {
                try {
                    renderPath();
                } catch (Exception e) {
                    PATH_LOG.error("Error rendering path", e);
                }
            }
        }

        if (pathingBehavior.isPathing()) {
            Optional<Rotation> currentRotation = Optional.ofNullable(lookBehavior.currentRotation);
            var req = InputRequest.builder()
                .input(inputOverrideHandler.currentInput)
                .priority(MOVEMENT_PRIORITY);
            currentRotation.ifPresent(rotation -> req
                .yaw(rotation.yaw())
                .pitch(rotation.pitch()));
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

    private Timer renderPathTimer = Timers.tickTimer();

    private void renderPath() {
        if (Proxy.getInstance().getActiveConnections().isEmpty()) return;
        var pathOptional = pathingBehavior.getPath();
        if (pathOptional.isEmpty()) return;
        IPath path = pathOptional.get();
        int pathPosition = pathingBehavior.getCurrent().getPosition();
        List<ClientboundLevelParticlesPacket> packets = CONFIG.client.extra.pathfinder.renderPathDetailed
            ? renderPathDetailed(path.positions(), pathPosition)
            : renderPathSimple(path.positions(), pathPosition);

        var connections = Proxy.getInstance().getActiveConnections().getArray();
        for (int i = 0; i < connections.length; i++) {
            var session = connections[i];
            for (int j = 0; j < packets.size(); j++) {
                session.sendAsync(packets.get(j));
            }
        }
    }

    private List<ClientboundLevelParticlesPacket> renderPathSimple(List<BlockPos> path, int pathPosition) {
        var particle = new Particle(ParticleType.SMALL_FLAME, null);
        return path.stream()
            .skip(pathPosition)
            .map(pos -> new ClientboundLevelParticlesPacket(
                particle,
                true,
                pos.x() + 0.5f,
                pos.y() + 0.5f,
                pos.z() + 0.5f,
                0,
                0,
                0,
                0f,
                1))
            .toList();
    }

    private List<ClientboundLevelParticlesPacket> renderPathDetailed(List<BlockPos> path, int pathPosition) {
        var middlePosParticle = new Particle(ParticleType.SOUL_FIRE_FLAME, null);
        var lineParticle = new Particle(ParticleType.SMALL_FLAME, null);
        List<ClientboundLevelParticlesPacket> packets = new ArrayList<>(path.size() - pathPosition);
        BlockPos prevPos = path.get(pathPosition);
        packets.add(new ClientboundLevelParticlesPacket(
            middlePosParticle,
            true,
            prevPos.x() + 0.5f,
            prevPos.y() + 0.5f,
            prevPos.z() + 0.5f,
            0,
            0,
            0,
            0f,
            1));
        for (int i = pathPosition+1; i < path.size(); i++) {
            BlockPos blockPos = path.get(i);
            packets.add(new ClientboundLevelParticlesPacket(
                middlePosParticle,
                true,
                blockPos.x() + 0.5f,
                blockPos.y() + 0.5f,
                blockPos.z() + 0.5f,
                0,
                0,
                0,
                0f,
                1));
            // create "line" particle every 0.2 between prev and current
            double xDiff = blockPos.x() - prevPos.x();
            double yDiff = blockPos.y() - prevPos.y();
            double zDiff = blockPos.z() - prevPos.z();
            double distance = Math.sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff);
            double xStep = xDiff / distance;
            double yStep = yDiff / distance;
            double zStep = zDiff / distance;
            double x = prevPos.x() + 0.5;
            double y = prevPos.y() + 0.5;
            double z = prevPos.z() + 0.5;
            for (double j = 0; j < distance; j += 0.2) {
                x += xStep * 0.2;
                y += yStep * 0.2;
                z += zStep * 0.2;
                packets.add(new ClientboundLevelParticlesPacket(
                    lineParticle,
                    true,
                    x,
                    y,
                    z,
                    0,
                    0,
                    0,
                    0f,
                    1));
            }
            prevPos = blockPos;
        }
        return packets;
    }
}
