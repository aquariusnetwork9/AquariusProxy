package com.zenith.module.impl;

import com.zenith.Proxy;
import com.zenith.cache.data.entity.Entity;
import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.module.ClientBotTick;
import com.zenith.event.module.SpawnPatrolTargetAcquiredEvent;
import com.zenith.event.module.SpawnPatrolTargetKilledEvent;
import com.zenith.event.proxy.DeathEvent;
import com.zenith.event.proxy.PlayerAttackedUsEvent;
import com.zenith.event.proxy.chat.DeathMessageChatEvent;
import com.zenith.feature.pathfinder.Baritone;
import com.zenith.feature.pathfinder.goals.Goal;
import com.zenith.feature.pathfinder.goals.GoalNear;
import com.zenith.feature.pathfinder.goals.GoalXZ;
import com.zenith.feature.world.Input;
import com.zenith.feature.world.InputRequest;
import com.zenith.feature.world.World;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.mc.dimension.DimensionData;
import com.zenith.mc.dimension.DimensionRegistry;
import com.zenith.module.Module;
import com.zenith.util.Timer;
import com.zenith.util.Timers;
import com.zenith.util.math.MathHelper;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Shared.*;

public class SpawnPatrol extends Module {
    private final Timer pathTimer = Timers.tickTimer();
    private final Timer killTimer = Timers.tickTimer();
    public static final int MOVEMENT_PRIORITY = 150;
    private double lastX = Double.MIN_VALUE;
    private double lastY = Double.MIN_VALUE;
    private double lastZ = Double.MIN_VALUE;
    private int spookTarget = -1;
    @Nullable private GameProfile spookTargetProfile = null;
    private long lastDeath = 0;

    @Override
    public void subscribeEvents() {
        EVENT_BUS.subscribe(
            this,
            of(ClientBotTick.class, this::handleBotTick),
            of(ClientBotTick.Starting.class, this::handleBotTickStarting),
            of(DeathMessageChatEvent.class, this::handleDeathMessage),
            of(PlayerAttackedUsEvent.class, this::handlePlayerAttackedUs),
            of(DeathEvent.class, this::handleDeathEvent)
        );
    }

    private void handleDeathEvent(DeathEvent event) {
        lastDeath = System.currentTimeMillis();
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.spawnPatrol.enabled;
    }

    @Override
    public void onDisable() {
        Baritone.INSTANCE.stop();
        spookTarget = -1;
    }

    private void handleBotTickStarting(ClientBotTick.Starting event) {
        pathTimer.reset();
        killTimer.reset();
        spookTarget = -1;
    }

    private void awaitSlashKill() {
        if (System.currentTimeMillis() - lastDeath > 10000) {
            warn("/kill seems to have failed :(");
            warn("And we seem to be stuck");
            walkForwardAndJumpForAwhile(500);
        }
    }

    private ScheduledFuture<?> walkForwardAndJumpFuture = null;

    private void walkForwardAndJumpForAwhile(int ticks) {
        if (walkForwardAndJumpFuture != null && !walkForwardAndJumpFuture.isDone()) {
            warn("Already walking forward and jumping");
            return;
        }
        walkForwardAndJumpFuture = Proxy.getInstance().getClient().getClientEventLoop().scheduleAtFixedRate(() -> {
            if (Baritone.INSTANCE.getPathingBehavior().getFailedPathSearches().get() == 0) {
                walkForwardAndJumpFuture.cancel(true);
                return;
            }

            var in = Input.builder()
                .pressingForward(true)
                .jumping(true)
                .build();
            var req = InputRequest.builder()
                .input(in)
                .priority(MOVEMENT_PRIORITY);
            if (ThreadLocalRandom.current().nextFloat() > 0.95f) {
                req.yaw(ThreadLocalRandom.current().nextFloat() * 360);
            }
            INPUTS.submit(req.build());
        }, 0, 50, TimeUnit.MILLISECONDS);
        Proxy.getInstance().getClient().getClientEventLoop().schedule(() -> {
            if (walkForwardAndJumpFuture != null) {
                walkForwardAndJumpFuture.cancel(true);
                walkForwardAndJumpFuture = null;
            }
        }, ticks * 50L, TimeUnit.MILLISECONDS);
    }

    private void handleBotTick(ClientBotTick event) {
        if (CONFIG.client.extra.spawnPatrol.kill && killTimer.tick(20L * CONFIG.client.extra.spawnPatrol.killSeconds) && !MODULE.get(KillAura.class).isActive()) {
            double dist = MathHelper.distance3d(lastX, lastY, lastZ, CACHE.getPlayerCache().getX(), CACHE.getPlayerCache().getY(), CACHE.getPlayerCache().getZ());
            if (dist < CONFIG.client.extra.spawnPatrol.killMinDist) {
                info("sending /kill. expected: {} actual: {}", CONFIG.client.extra.spawnPatrol.killMinDist, dist);
                sendClientPacketAsync(new ServerboundChatPacket("/kill"));
                if (CONFIG.client.extra.spawnPatrol.killAntiStuck) {
                    EXECUTOR.schedule(this::awaitSlashKill, 5L, TimeUnit.SECONDS);
                }
            }
            lastX = CACHE.getPlayerCache().getX();
            lastY = CACHE.getPlayerCache().getY();
            lastZ = CACHE.getPlayerCache().getZ();
        }

        if (pathTimer.tick(20L)) {
            boolean netherPathing = false;
            if (CONFIG.client.extra.spawnPatrol.nether && !Baritone.INSTANCE.getGetToBlockProcess().isActive() && !Baritone.INSTANCE.getFollowProcess().isActive()) {
                DimensionData currentDimension = World.getCurrentDimension();
                if (currentDimension.id() != DimensionRegistry.THE_NETHER.id()) {
                    Baritone.INSTANCE.getTo(BlockRegistry.NETHER_PORTAL);
                    netherPathing = true;
                }
            }
            if (!Baritone.INSTANCE.isActive() && !Baritone.INSTANCE.getFollowProcess().isActive() && !netherPathing) {
                if (CONFIG.client.extra.spawnPatrol.random) {
                    pathRandom();
                } else {
                    pathToGoal();
                }
            }
            if (CONFIG.client.extra.spawnPatrol.spook) {
                if (CONFIG.client.extra.spawnPatrol.spookStickyTarget) {
                    boolean targetUnset = spookTarget == -1;
                    EntityLiving spookTargetEntity = null;
                    if (spookTarget != -1) {
                        var e = CACHE.getEntityCache().get(spookTarget);
                        if (e instanceof EntityLiving el) spookTargetEntity = el;
                    }
                    boolean targetEntityExists = !targetUnset && spookTargetEntity != null;
                    if (targetUnset) {
                        Optional<EntityPlayer> potentialTarget = anyoneToSpookTarget();
                        if (potentialTarget.isPresent()) {
                            spookTarget = potentialTarget.get().getEntityId();
                            var targetPlayerListEntry = CACHE.getTabListCache().get(potentialTarget.get().getUuid());
                            if (targetPlayerListEntry.isEmpty()) {
                                warn("Failed to get player list entry for spook target");
                                return;
                            }
                            spookTargetProfile = targetPlayerListEntry.get().getProfile();
                            info("Target Acquired: {}", targetPlayerListEntry.map(PlayerListEntry::getName).orElse("???"));
                            EVENT_BUS.postAsync(new SpawnPatrolTargetAcquiredEvent(potentialTarget.get(), targetPlayerListEntry.get()));
                            Baritone.INSTANCE.follow(potentialTarget.get());
                        }
                    } else if (!targetEntityExists) {
                        info("Target escaped :(");
                        spookTarget = -1;
                        spookTargetProfile = null;
                        Baritone.INSTANCE.getFollowProcess().onLostControl();
                    } else {
                        if (!Baritone.INSTANCE.getFollowProcess().isActive()) {
                            Baritone.INSTANCE.follow(spookTargetEntity);
                        }
                    }
                } else if (anyoneToSpook()) {
                    if (!Baritone.INSTANCE.getFollowProcess().isActive()) {
                        Baritone.INSTANCE.follow(this::spookFilter);
                    }
                }
            }
        }
    }

    private void handleDeathMessage(DeathMessageChatEvent event) {
        if (!CONFIG.client.extra.spawnPatrol.spookStickyTarget) return;
        var profile = spookTargetProfile;
        if (profile == null) return;
        if (event.deathMessage().victim().equals(profile.getName())) {
            info("Spook target killed :)");
            EVENT_BUS.postAsync(new SpawnPatrolTargetKilledEvent(profile, event.component(), event.message(), event.deathMessage()));
        }
    }

    private void handlePlayerAttackedUs(PlayerAttackedUsEvent event) {
        if (!CONFIG.client.extra.spawnPatrol.spookStickyTarget || !CONFIG.client.extra.spawnPatrol.spookAttackers) return;
        int currentTargetId = spookTarget;
        EntityPlayer newTarget = event.attacker();
        if (spookTarget == newTarget.getEntityId()) return;
        if (currentTargetId != -1) {
            Entity currentTarget = CACHE.getEntityCache().get(currentTargetId);
            if (currentTarget instanceof EntityPlayer player) {
                double distanceToCurrentTarget = CACHE.getPlayerCache().distanceSqToSelf(player);
                if (distanceToCurrentTarget < Math.pow(8, 2)) {
                    debug("Ignoring new target because current target is very close");
                    return;
                }
            }
        }
        if (PLAYER_LISTS.getSpawnPatrolIgnoreList().contains(newTarget.getUuid())) return;
        spookTarget = newTarget.getEntityId();
        var targetPlayerListEntry = CACHE.getTabListCache().get(newTarget.getUuid());
        if (targetPlayerListEntry.isEmpty()) {
            warn("Failed to get player list entry for spook target");
            return;
        }
        spookTargetProfile = targetPlayerListEntry.get().getProfile();
        info("Attacker Target Acquired: {}", targetPlayerListEntry.map(PlayerListEntry::getName).orElse("???"));
        Baritone.INSTANCE.follow(newTarget);
        EVENT_BUS.postAsync(new SpawnPatrolTargetAcquiredEvent(newTarget, targetPlayerListEntry.get()));
    }

    private boolean anyoneToSpook() {
        return CACHE.getEntityCache().getEntities().values().stream()
            .filter(e -> e instanceof EntityPlayer)
            .map(e -> (EntityPlayer) e)
            .anyMatch(this::spookFilter);
    }

    private Optional<EntityPlayer> anyoneToSpookTarget() {
        return CACHE.getEntityCache().getEntities().values().stream()
            .filter(e -> e instanceof EntityPlayer)
            .map(e -> (EntityPlayer) e)
            .filter(this::spookFilter)
            .findFirst();
    }

    private boolean spookFilter(EntityLiving e) {
        if (!(e instanceof EntityPlayer player)) return false;
        if (player.isSelfPlayer()) return false;
        if (PLAYER_LISTS.getSpawnPatrolIgnoreList().contains(player.getUuid())) return false;
        if (CONFIG.client.extra.spawnPatrol.spookIgnoreFriends && PLAYER_LISTS.getFriendsList().contains(player.getUuid())) return false;
        if (CONFIG.client.extra.spawnPatrol.spookOnlyNakeds) {
            int equipCount = 0;
            for (var equipEntry : e.getEquipment().entrySet()) {
                if (equipEntry.getKey() == EquipmentSlot.MAIN_HAND || equipEntry.getKey() == EquipmentSlot.OFF_HAND) continue;
                if (equipEntry.getValue() != Container.EMPTY_STACK) equipCount++;
            }
            if (equipCount > 1) return false;
        }
        return true;
    }

    private void pathToGoal() {
        Goal goal = CONFIG.client.extra.spawnPatrol.goalXZ ?
            new GoalXZ(CONFIG.client.extra.spawnPatrol.goalX, CONFIG.client.extra.spawnPatrol.goalZ) :
            new GoalNear(CONFIG.client.extra.spawnPatrol.goalX, CONFIG.client.extra.spawnPatrol.goalY, CONFIG.client.extra.spawnPatrol.goalZ, 10 * 10);
        if (goal.isInGoal(
            MathHelper.floorI(CACHE.getPlayerCache().getX()),
            MathHelper.floorI(CACHE.getPlayerCache().getY()),
            MathHelper.floorI(CACHE.getPlayerCache().getZ()))
        ) {
            info("Reached goal");
            pathRandom();
        } else {
            info("Pathing to goal: {}", goal);
            Baritone.INSTANCE.goal(goal);
        }
    }

    private void pathRandom() {
        double randomXOff = ((Math.random() - 0.5) * 500);
        randomXOff += Math.signum(randomXOff) * 100;
        double randomZOff = ((Math.random() - 0.5) * 500);
        randomZOff += Math.signum(randomZOff) * 100;
        var goal = new GoalXZ(MathHelper.floorI(randomXOff + CACHE.getPlayerCache().getX()), MathHelper.floorI(randomZOff + CACHE.getPlayerCache().getZ()));
        info("Pathing to {}", goal);
        Baritone.INSTANCE.pathTo(goal);
    }
}
