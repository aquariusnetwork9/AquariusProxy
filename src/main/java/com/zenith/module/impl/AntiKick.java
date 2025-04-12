package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.zenith.Proxy;
import com.zenith.event.client.ClientTickEvent;
import com.zenith.event.module.ClientSwingEvent;
import com.zenith.event.module.EntityFishHookSpawnEvent;
import com.zenith.event.player.PlayerConnectedEvent;
import com.zenith.module.api.Module;
import com.zenith.util.Timer;
import com.zenith.util.Timers;
import com.zenith.util.math.MathHelper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CONFIG;

public class AntiKick extends Module {
    private double lastPosX = 0;
    private double lastPosZ = 0;
    private Instant lastPosTime = Instant.now();
    private Instant lastFishingTime = Instant.now();
    private Instant lastSwingTime = Instant.now();
    private final Timer timer = Timers.timer();

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(PlayerConnectedEvent.class, this::onProxyClientConnectedEvent),
            of(ClientTickEvent.class, this::onClientTick),
            of(EntityFishHookSpawnEvent.class, this::onEntityFishHookSpawnEvent),
            of(ClientSwingEvent.class, this::onClientSwingEvent)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.antiKick.enabled;
    }

    @Override
    public void onEnable() {
        reset();
    }

    private synchronized void reset() {
        this.lastPosX = CACHE.getPlayerCache().getX();
        this.lastPosZ = CACHE.getPlayerCache().getZ();
        this.lastPosTime = this.lastFishingTime = this.lastSwingTime = Instant.now();
    }

    public void onProxyClientConnectedEvent(final PlayerConnectedEvent event) {
        reset();
    }

    public void onClientTick(final ClientTickEvent event) {
        if (!timer.tick(10 * 1000)) return; // process event every 10 seconds
        if (!Proxy.getInstance().hasActivePlayer()) return;

        final var fishBypass = bypassedKickWithFishing();
        final var swingBypass = bypassedKickWithSwing();
        final var walkBypass = bypassedKickWithWalk();
        if (fishBypass || swingBypass || walkBypass) return;
        final var currentPlayer = Proxy.getInstance().getCurrentPlayer().get();
        if (currentPlayer == null) return;
        currentPlayer.disconnect("[AntiKick] Kicked for inactivity");
    }

    public void onEntityFishHookSpawnEvent(final EntityFishHookSpawnEvent event) {
        if (event.getOwnerEntityId() == CACHE.getPlayerCache().getEntityId())
            lastFishingTime = Instant.now();
    }

    public void onClientSwingEvent(final ClientSwingEvent event) {
        lastSwingTime = Instant.now();
    }

    private boolean bypassedKickWithFishing() {
        return Instant.now()
            .minus(Duration.ofMinutes(CONFIG.client.extra.antiKick.playerInactivityKickMins))
            .isBefore(lastFishingTime);
    }

    private boolean bypassedKickWithSwing() {
        return Instant.now()
            .minus(Duration.ofMinutes(CONFIG.client.extra.antiKick.playerInactivityKickMins))
            .isBefore(lastSwingTime);
    }

    private boolean bypassedKickWithWalk() {
        final var distance = MathHelper.manhattanDistance2d(lastPosX, lastPosZ, CACHE.getPlayerCache().getX(), CACHE.getPlayerCache().getZ());
        if (distance >= CONFIG.client.extra.antiKick.minWalkDistance) {
            lastPosX = CACHE.getPlayerCache().getX();
            lastPosZ = CACHE.getPlayerCache().getZ();
            lastPosTime = Instant.now();
            return true;
        } else {
            return Instant.now()
                .minus(Duration.ofMinutes(CONFIG.client.extra.antiKick.playerInactivityKickMins))
                .isBefore(lastPosTime);
        }
    }
}
