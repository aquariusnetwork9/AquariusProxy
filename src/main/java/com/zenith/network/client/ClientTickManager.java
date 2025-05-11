package com.zenith.network.client;

import com.zenith.Proxy;
import com.zenith.event.client.ClientBotTick;
import com.zenith.event.client.ClientDisconnectEvent;
import com.zenith.event.client.ClientOnlineEvent;
import com.zenith.event.client.ClientTickEvent;
import com.zenith.event.player.PlayerConnectedEvent;
import com.zenith.event.player.PlayerDisconnectedEvent;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.CLIENT_LOG;
import static com.zenith.Globals.EVENT_BUS;
import static java.util.Objects.nonNull;

public class ClientTickManager {
    protected ScheduledFuture<?> clientTickFuture;
    private final AtomicBoolean doBotTicks = new AtomicBoolean(false);

    public ClientTickManager() {
        EVENT_BUS.subscribe(
            this,
            of(ClientOnlineEvent.class, this::handlePlayerOnlineEvent),
            of(PlayerConnectedEvent.class, this::handleProxyClientConnectedEvent),
            of(PlayerDisconnectedEvent.class, this::handleProxyClientDisconnectedEvent),
            of(ClientDisconnectEvent.class, this::handleDisconnectEvent)
        );
    }

    public void handlePlayerOnlineEvent(final ClientOnlineEvent event) {
        Proxy.getInstance().getClient().executeInEventLoop(() -> {
            if (!Proxy.getInstance().hasActivePlayer()) {
                startBotTicks();
            }
        });
    }

    public void handleDisconnectEvent(final ClientDisconnectEvent event) {
        stopBotTicks();
    }

    public void handleProxyClientConnectedEvent(final PlayerConnectedEvent event) {
        Proxy.getInstance().getClient().executeInEventLoop(() -> {
            stopBotTicks();
        });
    }

    public void handleProxyClientDisconnectedEvent(final PlayerDisconnectedEvent event) {
        Proxy.getInstance().getClient().executeInEventLoop(() -> {
            if (nonNull(Proxy.getInstance().getClient()) && Proxy.getInstance().getClient().isOnline()) {
                startBotTicks();
            }
        });
    }

    public synchronized void startClientTicks() {
        if (this.clientTickFuture == null || this.clientTickFuture.isDone()) {
            CLIENT_LOG.debug("Starting Client Ticks");
            EVENT_BUS.post(ClientTickEvent.Starting.INSTANCE);
            var eventLoop = Proxy.getInstance().getClient().getClientEventLoop();
            this.clientTickFuture = eventLoop.scheduleWithFixedDelay(this::tick, 0, 50, TimeUnit.MILLISECONDS);
        }
    }

    private static final long LONG_TICK_THRESHOLD_MS = 100L;
    private static final long LONG_TICK_WARNING_INTERVAL_MS = TimeUnit.SECONDS.toMillis(60);
    private long lastLongTickWarning = 0L;

    private void tick() {
        try {
            long before = System.currentTimeMillis();
            EVENT_BUS.post(ClientTickEvent.INSTANCE);
            if (doBotTicks.get()) {
                EVENT_BUS.post(ClientBotTick.INSTANCE);
            }
            long after = System.currentTimeMillis();
            long elapsedMs = after - before;
            if (elapsedMs > LONG_TICK_THRESHOLD_MS) {
                if (System.currentTimeMillis() - lastLongTickWarning > LONG_TICK_WARNING_INTERVAL_MS) {
                    CLIENT_LOG.warn("Slow Client Tick: {}ms", elapsedMs);
                    lastLongTickWarning = System.currentTimeMillis();
                } else {
                    CLIENT_LOG.debug("Slow Client Tick: {}ms", elapsedMs);
                }
            }
        } catch (final Throwable e) {
            CLIENT_LOG.error("Error during client tick", e);
        }
    };

    public synchronized void stopClientTicks() {
        if (this.clientTickFuture != null && !this.clientTickFuture.isDone()) {
            this.clientTickFuture.cancel(false);
            try {
                this.clientTickFuture.get(1L, TimeUnit.SECONDS);
            } catch (final Exception e) {
                // fall through
            }
            if (doBotTicks.compareAndExchange(true, false)) {
                CLIENT_LOG.debug("Stopped Bot Ticks");
                EVENT_BUS.post(ClientBotTick.Stopped.INSTANCE);
            }
            CLIENT_LOG.debug("Stopped Client Ticks");
            EVENT_BUS.post(ClientTickEvent.Stopped.INSTANCE);
            this.clientTickFuture = null;
        }
    }

    public void startBotTicks() {
        if (doBotTicks.compareAndSet(false, true)) {
            CLIENT_LOG.debug("Starting Bot Ticks");
            EVENT_BUS.post(ClientBotTick.Starting.INSTANCE);
        }
    }

    public void stopBotTicks() {
        if (doBotTicks.compareAndSet(true, false)) {
            Proxy.getInstance().getClient().executeInEventLoop(() -> {
                CLIENT_LOG.debug("Stopped Bot Ticks");
                EVENT_BUS.post(ClientBotTick.Stopped.INSTANCE);
            });
        }
    }
}
