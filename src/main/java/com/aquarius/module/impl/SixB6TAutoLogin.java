package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.Proxy;
import com.aquarius.event.chat.SystemChatEvent;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.event.client.ClientConnectEvent;
import com.aquarius.event.client.PlayerSetbackEvent;
import com.aquarius.event.sixb6t.SixB6TPhaseChangedEvent;
import com.aquarius.feature.player.Input;
import com.aquarius.feature.player.InputRequest;
import com.aquarius.feature.sixb6t.SixB6TJoin;
import com.aquarius.feature.sixb6t.SixB6TPhase;
import com.aquarius.module.api.Module;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.*;
import static com.aquarius.util.config.Config.Authentication.AccountType.OFFLINE;

/**
 * Carries a cracked (offline) 6b6t account through the auth lobby -> main lobby -> real world
 * login relay unattended, and gives up on a PicoLimbo backup-server stall after a patience
 * window instead of spinning forever.
 *
 * {@link SixB6TJoin} does the phase classification; this module owns the side effects: sending
 * "/login <password>" once the auth lobby prompts for it, walking through the portal once the
 * lobby teleports us to it (reusing the {@code INPUTS} tick-based movement AntiAFK already uses,
 * since Baritone/pathfinding needs more than the few chunks a fresh lobby join has loaded), and
 * disconnecting (so AutoReconnect can retry later) if PicoLimbo doesn't clear in time.
 */
public class SixB6TAutoLogin extends Module {
    private static final long LOGIN_COMMAND_DELAY_MS = 2000; // 6b6t's TPS swings hard; give the prompt time to land

    private volatile boolean loginSent = false;
    private volatile boolean awaitingPortalTeleport = false;
    private volatile int walkTicksRemaining = 0;
    private volatile float walkYaw = 0f;
    private @Nullable Future<?> limboTimeoutFuture;

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.sixB6TAutoLogin.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientConnectEvent.class, this::handleConnect),
            of(SixB6TPhaseChangedEvent.class, this::handlePhaseChanged),
            of(SystemChatEvent.class, this::handleSystemChat),
            of(PlayerSetbackEvent.class, this::handlePlayerSetback),
            of(ClientBotTick.class, this::handleClientTick)
        );
    }

    @Override
    public void onDisable() {
        reset();
    }

    private boolean active() {
        return Proxy.getInstance().isOn6b6t() && CONFIG.authentication.accountType == OFFLINE;
    }

    public void handleConnect(ClientConnectEvent event) {
        reset();
        SixB6TJoin.reset();
    }

    public void handlePhaseChanged(SixB6TPhaseChangedEvent event) {
        if (!active()) return;
        if (event.to() == SixB6TPhase.AUTH_LOBBY) {
            loginSent = false;
            awaitingPortalTeleport = false;
        }
        if (event.to() == SixB6TPhase.PICO_LIMBO) {
            scheduleLimboTimeout();
        } else {
            cancelLimboTimeout();
        }
        if (event.to() == SixB6TPhase.REAL_WORLD) {
            info("6b6t login relay complete, in the real world");
        }
    }

    public void handleSystemChat(SystemChatEvent event) {
        if (!active() || SixB6TJoin.getPhase() != SixB6TPhase.AUTH_LOBBY) return;
        var lower = event.message().toLowerCase(Locale.ROOT);
        if (!loginSent && lower.contains("please login with the command")) {
            var password = CONFIG.authentication.crackedLoginPassword;
            if (password == null || password.isEmpty()) {
                error("6b6t auth lobby is asking to /login, but no authentication.crackedLoginPassword is configured");
                return;
            }
            loginSent = true;
            EXECUTOR.schedule(() -> sendClientPacketAsync(new ServerboundChatPacket("/login " + password)),
                LOGIN_COMMAND_DELAY_MS, TimeUnit.MILLISECONDS);
        } else if (lower.contains("you are now logged in")) {
            awaitingPortalTeleport = true;
        }
    }

    public void handlePlayerSetback(PlayerSetbackEvent event) {
        if (!active() || !awaitingPortalTeleport) return;
        awaitingPortalTeleport = false;
        walkYaw = CACHE.getPlayerCache().getYaw();
        walkTicksRemaining = CONFIG.client.extra.sixB6TAutoLogin.portalWalkTicks;
        debug("Walking through the auth lobby portal ({} ticks)", walkTicksRemaining);
    }

    public void handleClientTick(ClientBotTick event) {
        if (!active() || walkTicksRemaining <= 0) return;
        walkTicksRemaining--;
        INPUTS.submit(InputRequest.builder()
            .owner(this)
            .input(Input.builder().pressingForward(true).build())
            .yaw(walkYaw)
            .priority(50000)
            .build());
    }

    private void scheduleLimboTimeout() {
        cancelLimboTimeout();
        var patienceSeconds = CONFIG.client.extra.sixB6TAutoLogin.limboPatienceSeconds;
        limboTimeoutFuture = EXECUTOR.schedule(this::handleLimboTimeout, patienceSeconds, TimeUnit.SECONDS);
    }

    private void cancelLimboTimeout() {
        if (limboTimeoutFuture != null) {
            limboTimeoutFuture.cancel(false);
            limboTimeoutFuture = null;
        }
    }

    private void handleLimboTimeout() {
        if (SixB6TJoin.getPhase() != SixB6TPhase.PICO_LIMBO) return; // cleared already
        info("Stuck in 6b6t's backup lobby (PicoLimbo) past the patience window, reconnecting");
        Proxy.getInstance().disconnect("6b6t limbo timeout");
    }

    private void reset() {
        loginSent = false;
        awaitingPortalTeleport = false;
        walkTicksRemaining = 0;
        cancelLimboTimeout();
    }
}
