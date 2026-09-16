package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.Proxy;
import com.aquarius.event.chat.SystemChatEvent;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.event.client.ClientConnectEvent;
import com.aquarius.feature.sixb6t.SixB6TTpaRequestParser;
import com.aquarius.module.api.Module;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.*;

/**
 * Auto-accepts "/tpa" requests on 6b6t with "/tpy &lt;name&gt;", ported from the already-proven
 * client-side {@code tpy-accept} Fabric mod (same whitelist-gated design, same
 * {@link SixB6TTpaRequestParser} chat-shape gate against the player-chat-spoof risk).
 *
 * No global cooldown -- several whitelisted accounts can request at once, so the repeat guard is
 * per requester. Accepts are still queued and drained one per {@code sendSpacingMs} on the client
 * tick (never sent straight from the chat handler) so a burst cannot look like chat-spam to 6b6t.
 */
public class SixB6TTpaAutoAccept extends Module {
    private final Map<String, Long> lastAccepted = new HashMap<>();
    private final Deque<String> pending = new ArrayDeque<>();
    private volatile long lastSentAt = 0L;

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.sixB6TTpaAutoAccept.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientConnectEvent.class, this::handleConnect),
            of(SystemChatEvent.class, this::handleSystemChat),
            of(ClientBotTick.class, this::handleClientTick)
        );
    }

    @Override
    public void onDisable() {
        pending.clear();
        lastAccepted.clear();
    }

    public void handleConnect(ClientConnectEvent event) {
        // A reconnect makes both stale: the queue would target a dead connection, and a repeat
        // guard from before the disconnect should not suppress a fresh request after rejoining.
        pending.clear();
        lastAccepted.clear();
    }

    public void handleSystemChat(SystemChatEvent event) {
        if (!Proxy.getInstance().isOn6b6t()) return;
        var requester = SixB6TTpaRequestParser.requesterIn(event.message());
        if (requester == null) return;

        var whitelist = CONFIG.client.extra.sixB6TTpaAutoAccept.whitelist;
        var isWhitelisted = whitelist.stream().anyMatch(name -> name.equalsIgnoreCase(requester));
        if (!isWhitelisted) {
            debug("Ignoring a teleport request from {} (not whitelisted)", requester);
            return;
        }

        var key = requester.toLowerCase(Locale.ROOT);
        var now = System.currentTimeMillis();
        var last = lastAccepted.get(key);
        if (last != null && now - last < CONFIG.client.extra.sixB6TTpaAutoAccept.repeatWindowMs) return;
        lastAccepted.put(key, now);
        pending.addLast(requester);
    }

    public void handleClientTick(ClientBotTick event) {
        if (pending.isEmpty()) return;
        var now = System.currentTimeMillis();
        if (now - lastSentAt < CONFIG.client.extra.sixB6TTpaAutoAccept.sendSpacingMs) return;
        var requester = pending.pollFirst();
        lastSentAt = now;
        sendClientPacketAsync(new ServerboundChatPacket("/tpy " + requester));
        info("Accepted teleport request from {}", requester);
    }
}
