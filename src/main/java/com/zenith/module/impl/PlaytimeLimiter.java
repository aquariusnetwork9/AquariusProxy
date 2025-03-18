package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.zenith.Proxy;
import com.zenith.event.module.ClientTickEvent;
import com.zenith.event.proxy.PlayerLoginEvent;
import com.zenith.event.proxy.ServerConnectionRemovedEvent;
import com.zenith.module.Module;
import net.kyori.adventure.text.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Shared.CONFIG;

public class PlaytimeLimiter extends Module {
    private Cache<InetAddress, Long> ipConnectedCache = CacheBuilder.newBuilder()
        .maximumSize(250)
        .build();
    private Cache<UUID, Long> uuidConnectedCache = CacheBuilder.newBuilder()
        .maximumSize(250)
        .build();

    @Override
    public boolean enabledSetting() {
        return CONFIG.server.playtimeLimiter.enabled;
    }

    @Override
    public void onEnable() {
        initIntervalCaches();
    }

    public void initIntervalCaches() {
        ipConnectedCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(CONFIG.server.playtimeLimiter.connectionIntervalCooldownSeconds))
            .maximumSize(250)
            .build();
        uuidConnectedCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(CONFIG.server.playtimeLimiter.connectionIntervalCooldownSeconds))
            .maximumSize(250)
            .build();
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(PlayerLoginEvent.class, this::handlePlayerLogin),
            of(ServerConnectionRemovedEvent.class, this::handleServerConnectionRemoved),
            of(ClientTickEvent.class, this::handleClientTick)
        );
    }

    private void handleClientTick(ClientTickEvent event) {
        if (!CONFIG.server.playtimeLimiter.limitSessionLength) return;
        var connections = Proxy.getInstance().getActiveConnections().getArray();
        if (CONFIG.server.playtimeLimiter.allowSpectatorFallback) {
            var activePlayer = Proxy.getInstance().getActivePlayer();
            if (activePlayer == null) return;
        }
        for (int i = 0; i < connections.length; i++) {
            var connection = connections[i];
            if (CONFIG.server.playtimeLimiter.allowSpectatorFallback) {
                if (connection.isSpectator()) continue;
            }
            if (System.currentTimeMillis() - connection.getConnectionTimeEpochMs() > CONFIG.server.playtimeLimiter.maxSessionLengthSeconds * 1000L) {
                if (CONFIG.server.playtimeLimiter.allowSpectatorFallback) {
                    connection.transferToSpectator();
                } else {
                    connection.disconnect(Component.text("[ZenithProxy] Playtime limit reached. Come back later!"));
                }
            }
        }
    }

    private void handlePlayerLogin(PlayerLoginEvent event) {
        if (!CONFIG.server.playtimeLimiter.limitConnectionInterval) return;
        boolean shouldTransferToSpectator = false;
        if (CONFIG.server.playtimeLimiter.allowSpectatorFallback) {
            if (event.serverConnection().isSpectator()) return;
            else shouldTransferToSpectator = true;
        }
        var uuid = event.serverConnection().getLoginProfileUUID();
        if (uuid != null) {
            Long dcTime = uuidConnectedCache.getIfPresent(uuid);
            if (dcTime != null) {
                var duration = System.currentTimeMillis() - dcTime;
                var msUntilCooldown = CONFIG.server.playtimeLimiter.connectionIntervalCooldownSeconds * 1000L - duration;
                if (msUntilCooldown > 0) {
                    if (shouldTransferToSpectator) {
                        event.serverConnection().transferToSpectator();
                    } else {
                        event.serverConnection().disconnect(Component.text("[ZenithProxy] You are on cooldown. Come back in " + (msUntilCooldown / 1000L) + " seconds!"));
                        return;
                    }
                }
            }
        }

        var socketAddress = (InetSocketAddress) event.serverConnection().getRemoteAddress();
        if (socketAddress != null) {
            Long dcTime = ipConnectedCache.getIfPresent(socketAddress);
            if (dcTime != null) {
                var duration = System.currentTimeMillis() - dcTime;
                var msUntilCooldown = CONFIG.server.playtimeLimiter.connectionIntervalCooldownSeconds * 1000L - duration;
                if (msUntilCooldown > 0) {
                    if (shouldTransferToSpectator) {
                        event.serverConnection().transferToSpectator();
                    } else {
                        event.serverConnection().disconnect(Component.text("[ZenithProxy] You are on cooldown. Come back in " + (msUntilCooldown / 1000L) + " seconds!"));
                    }
                }
            }
        }
    }

    private void handleServerConnectionRemoved(ServerConnectionRemovedEvent event) {
        if (CONFIG.server.playtimeLimiter.allowSpectatorFallback) {
            if (event.serverConnection().isSpectator()) return;
        }
        var socketAddress = (InetSocketAddress) event.serverConnection().getRemoteAddress();
        if (socketAddress != null) {
            ipConnectedCache.put(socketAddress.getAddress(), System.currentTimeMillis());
        }
        var uuid = event.serverConnection().getLoginProfileUUID();
        if (uuid != null) {
            uuidConnectedCache.put(uuid, System.currentTimeMillis());
        }
    }
}
