package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.google.common.collect.Sets;
import com.aquarius.Proxy;
import com.aquarius.event.client.ClientConnectEvent;
import com.aquarius.event.client.ClientDisconnectEvent;
import com.aquarius.event.module.AutoReconnectEvent;
import com.aquarius.module.api.Module;
import com.aquarius.util.Wait;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.*;
import static com.aquarius.util.DisconnectMessages.*;

public class AutoReconnect extends Module {
    private @Nullable Future<?> autoReconnectFuture;

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientDisconnectEvent.class, this::handleDisconnectEvent),
            of(ClientConnectEvent.class, this::handleConnectEvent)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.autoReconnect.enabled;
    }

    public boolean cancelAutoReconnect() {
        if (autoReconnectIsInProgress()) {
            var future = this.autoReconnectFuture;
            this.autoReconnectFuture = null;
            if (future != null) future.cancel(true);
            return true;
        }
        return false;
    }

    public boolean autoReconnectIsInProgress() {
        return this.autoReconnectFuture != null && !this.autoReconnectFuture.isDone();
    }

    @Override
    public void onDisable() {
        cancelAutoReconnect();
    }

    public void handleDisconnectEvent(ClientDisconnectEvent event) {
        if (shouldAutoDisconnectCancelAutoReconnect(event)) {
            info("Cancelling AutoReconnect due to AutoDisconnect cancelAutoReconnect");
            return;
        }
        if (isReconnectableDisconnect(event.reason())) {
            scheduleAutoReconnect(reconnectDelaySeconds(event.reason()));
        } else {
            info("Cancelling AutoReconnect because disconnect reason is not reconnectable");
        }
    }

    /**
     * 6b6t names its own cooldown exactly ("Your verification link expired. Please wait 84 minutes
     * before trying again.", "already online" kicks clear in seconds by contrast) — hammering the flat
     * AutoReconnect delaySeconds into one of the long ones is pure noise for the next N minutes and, on
     * a system that just told us to back off, is exactly the kind of signal an anti-bot layer is built
     * to notice. Honor the server's own number when it names one; a small buffer avoids landing a few
     * seconds early and re-earning the same cooldown. Live-observed 2026-09-23: this account and two
     * others in the same fleet sat in this exact loop for 100+ minutes each, every attempt failing.
     */
    private int reconnectDelaySeconds(final String reason) {
        final int configured = CONFIG.client.extra.autoReconnect.delaySeconds;
        if (reason == null) {
            return configured;
        }
        final Matcher matcher = WAIT_MINUTES_PATTERN.matcher(reason);
        if (!matcher.find()) {
            return configured;
        }
        final int minutes = Integer.parseInt(matcher.group(1));
        final int serverWaitSeconds = minutes * 60 + WAIT_BUFFER_SECONDS;
        if (serverWaitSeconds <= configured) {
            return configured;
        }
        info("server asked us to wait {} minute(s) before retrying — honoring that instead of the flat {}s AutoReconnect delay",
            minutes, configured);
        return serverWaitSeconds;
    }

    public void scheduleAutoReconnect(final int delaySeconds) {
        if (autoReconnectIsInProgress()) {
            info("AutoReconnect already in progress, not starting another");
            return;
        }
        this.autoReconnectFuture = EXECUTOR.submit(() -> autoReconnectRunnable(delaySeconds));
    }

    public boolean shouldAutoDisconnectCancelAutoReconnect(ClientDisconnectEvent event) {
        return CONFIG.client.extra.utility.actions.autoDisconnect.enabled
            && CONFIG.client.extra.utility.actions.autoDisconnect.cancelAutoReconnect
            && AutoDisconnect.isAutoDisconnectReason(event.reason());
    }

    public void handleConnectEvent(ClientConnectEvent event) {
        cancelAutoReconnect();
    }

    private void autoReconnectRunnable(int delaySeconds) {
        try {
            delayBeforeReconnect(delaySeconds);
            if (Thread.currentThread().isInterrupted()) return;
            EXECUTOR.execute(() -> {
                try {
                    Proxy.getInstance().connect();
                } catch (final Throwable e) {
                    DEFAULT_LOG.error("Error connecting", e);
                }
            });
            this.autoReconnectFuture = null;
        } catch (final Exception e) {
            info("AutoReconnect stopped");
        }
    }

    private void delayBeforeReconnect(int delaySeconds) {
        final int countdown = delaySeconds;
        EVENT_BUS.postAsync(new AutoReconnectEvent(countdown));
        // random jitter to help prevent multiple clients from logging in at the same time
        Wait.waitRandomMs(1000);
        // A short wait keeps the original every-10s countdown; a long, server-named one (see
        // reconnectDelaySeconds) would otherwise spam hundreds of "Reconnecting in Ns" lines for nothing.
        final int logIntervalSeconds = countdown > LONG_WAIT_LOG_THRESHOLD_SECONDS ? 60 : 10;
        for (int i = countdown; i > 0; i -= logIntervalSeconds) {
            info("Reconnecting in {}s", i);
            Wait.wait(Math.min(logIntervalSeconds, i));
        }
    }

    private static final Pattern WAIT_MINUTES_PATTERN = Pattern.compile("wait (\\d+) minutes?", Pattern.CASE_INSENSITIVE);
    private static final int WAIT_BUFFER_SECONDS = 30;
    private static final int LONG_WAIT_LOG_THRESHOLD_SECONDS = 60;

    private static final Set<String> NON_RECONNECTABLE_DISCONNECT_REASONS = Sets.newHashSet(
        SYSTEM_DISCONNECT,
        MANUAL_DISCONNECT,
        SERVER_CLOSING_MESSAGE,
        LOGIN_FAILED,
        AUTH_REQUIRED,
        MAX_PT_DISCONNECT,
        // A pitstop/goal logout schedules its OWN relogin (on its own timer) via scheduleAutoReconnect —
        // so the auto-reconnect handler must not fire a second reconnect here with the wrong delay.
        PITSTOP_DISCONNECT
    );

    private boolean isReconnectableDisconnect(final String reason) {
        if (NON_RECONNECTABLE_DISCONNECT_REASONS.contains(reason)) {
            return false;
        } else if (ActiveHours.isActiveHoursDisconnect(reason)) {
            return false;
        } else if (AutoDisconnect.isAutoDisconnectReason(reason)) {
            return (!CONFIG.client.extra.utility.actions.autoDisconnect.cancelAutoReconnect && !Proxy.getInstance().isPrio());
        } else {
            return true;
        }
    }
}
