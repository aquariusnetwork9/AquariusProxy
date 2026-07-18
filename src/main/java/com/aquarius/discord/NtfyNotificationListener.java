package com.aquarius.discord;

import com.aquarius.event.client.ClientConnectEvent;
import com.aquarius.event.client.ClientDisconnectEvent;
import com.aquarius.event.client.ClientLoginFailedEvent;
import com.aquarius.event.module.VisualRangeEnterEvent;
import com.aquarius.event.module.VisualRangeLeaveEvent;
import com.aquarius.event.module.VisualRangeLogoutEvent;
import com.aquarius.feature.api.ntfy.NtfyApi;
import com.aquarius.util.ChatUtil;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.EVENT_BUS;
import static com.github.rfresh2.EventConsumer.of;

/**
 * Ntfy counterpart to {@link NotificationEventListener} - a much smaller subset of events
 * (visual range + connection state), gated entirely by {@link com.aquarius.util.config.Config.Notifications.Ntfy}.
 * Kept as its own listener rather than folded into the Discord one so the two notification paths
 * stay independently toggleable and neither depends on the other being configured.
 */
public class NtfyNotificationListener {
    public static final NtfyNotificationListener INSTANCE = new NtfyNotificationListener();

    private NtfyNotificationListener() {}

    public void subscribeEvents() {
        EVENT_BUS.subscribe(
            this,
            of(VisualRangeEnterEvent.class, this::handleVisualRangeEnter),
            of(VisualRangeLeaveEvent.class, this::handleVisualRangeLeave),
            of(VisualRangeLogoutEvent.class, this::handleVisualRangeLogout),
            of(ClientConnectEvent.class, this::handleConnect),
            of(ClientDisconnectEvent.class, this::handleDisconnect),
            of(ClientLoginFailedEvent.class, this::handleLoginFailed)
        );
    }

    private void handleVisualRangeEnter(VisualRangeEnterEvent event) {
        NtfyApi.INSTANCE.notify(CONFIG.notifications.ntfy.visualRange, "Player in visual range",
            event.playerEntry().getName() + " entered visual range" + (event.isFriend() ? " (friend)" : ""));
    }

    private void handleVisualRangeLeave(VisualRangeLeaveEvent event) {
        NtfyApi.INSTANCE.notify(CONFIG.notifications.ntfy.visualRange, "Player left visual range",
            event.playerEntry().getName() + " left visual range" + (event.isFriend() ? " (friend)" : ""));
    }

    private void handleVisualRangeLogout(VisualRangeLogoutEvent event) {
        NtfyApi.INSTANCE.notify(CONFIG.notifications.ntfy.visualRange, "Player logged out in visual range",
            event.playerEntry().getName() + " logged out while in visual range" + (event.isFriend() ? " (friend)" : ""));
    }

    private void handleConnect(ClientConnectEvent event) {
        NtfyApi.INSTANCE.notify(CONFIG.notifications.ntfy.online, "Connected",
            "Connected to " + CONFIG.client.server.address);
    }

    private void handleDisconnect(ClientDisconnectEvent event) {
        NtfyApi.INSTANCE.notify(CONFIG.notifications.ntfy.offline, "Disconnected",
            "Disconnected: " + event.reason());
    }

    private void handleLoginFailed(ClientLoginFailedEvent event) {
        var message = event.exception() != null
            ? "Login failed: " + ChatUtil.constrainChatMessageSize(event.exception().getMessage(), true)
            : "Login failed";
        NtfyApi.INSTANCE.notify(CONFIG.notifications.ntfy.proxyEvent, "Login Failed", message);
    }
}
