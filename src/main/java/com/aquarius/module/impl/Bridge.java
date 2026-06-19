package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.Proxy;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.event.player.PlayerConnectionRemovedEvent;
import com.aquarius.feature.bridge.BridgeProtocol;
import com.aquarius.feature.bridge.BridgeWaypoint;
import com.aquarius.feature.bridge.ServerboundBridgePayloadHandler;
import com.aquarius.mc.dimension.DimensionData;
import com.aquarius.module.api.Module;
import com.aquarius.network.codec.PacketHandlerCodec;
import com.aquarius.network.codec.PacketHandlerStateCodec;
import com.aquarius.network.server.ServerSession;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

/**
 * Bridge — a generic, bidirectional link between AquariusProxy and the ProxyBridge client mod (a Meteor addon,
 * MC 1.21.4). It is the proxy half of the bridge: it pushes data the mod can display (live waypoints from
 * feature modules) and routes commands the mod sends back up.
 *
 * <h2>Display side (proxy -&gt; client)</h2>
 * Feature modules register a <i>waypoint source</i> via {@link #registerWaypointSource(String, Supplier)} keyed by
 * a group name. Each tick (throttled) the supplier is polled; when a group's contents change they are pushed to
 * every mod-present connection as a {@code wp/sync} (atomic full-group replace). PearlDrop's empty chambers are
 * wired up as the built-in {@code pearldrop.empties} source — because occupancy is recomputed live from the entity
 * cache, chambers that fill up simply drop out of the next sync and vanish on the client. No PearlDrop edits, no
 * diffing on the client.
 *
 * <h2>Control side (client -&gt; proxy)</h2>
 * The mod sends messages on {@code proxybridge:main}; {@link ServerboundBridgePayloadHandler} terminates that
 * channel and calls {@link #handleInbound}. Topic handlers are registered with
 * {@link #registerInboundHandler(String, BiConsumer)}. The built-in {@code cmd/invoke} handler runs a proxy command
 * — but only if its name is on {@code CONFIG.client.extra.bridge.invokeAllowList}.
 *
 * <p>The mod is detected either from its {@code minecraft:register} announcement (sniffed in the payload handler)
 * or from its {@code hello} handshake — either path marks the connection present and triggers a {@code hello} reply
 * plus an immediate re-publish of all groups.
 */
public class Bridge extends Module {

    public static final String SIDE = "proxy";
    public static final String VERSION = "1.0.0";

    public static final String GROUP_PEARLDROP_EMPTIES = "pearldrop.empties";

    private static final Key BRIDGE_CHANNEL = Key.key(BridgeProtocol.CHANNEL_NAMESPACE, BridgeProtocol.CHANNEL_PATH);

    private final Map<String, BiConsumer<ServerSession, BridgeProtocol.Reader>> inboundHandlers = new ConcurrentHashMap<>();
    private final Map<String, Supplier<List<BridgeWaypoint>>> waypointSources = new ConcurrentHashMap<>();
    /** UUIDs of connections known to have the mod. */
    private final java.util.Set<UUID> modPresent = ConcurrentHashMap.newKeySet();
    /** UUIDs we've already announced the channel to (clientbound minecraft:register), once per connection. */
    private final java.util.Set<UUID> channelRegistered = ConcurrentHashMap.newKeySet();
    /** Last-published contents per group, for change detection (broadcast only on change). */
    private final Map<String, List<BridgeWaypoint>> lastPublished = new ConcurrentHashMap<>();

    private int tickCounter = 0;

    public Bridge() {
        registerInboundHandler(BridgeProtocol.TOPIC_HELLO, this::onHello);
        registerInboundHandler(BridgeProtocol.TOPIC_CMD_INVOKE, this::onCmdInvoke);
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.bridge.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(PlayerConnectionRemovedEvent.class, this::onConnectionRemoved)
        );
    }

    @Override
    public PacketHandlerCodec registerServerPacketHandlerCodec() {
        return PacketHandlerCodec.serverBuilder()
            .setId("proxybridge")
            .setPriority(1000)
            .state(ProtocolState.GAME, PacketHandlerStateCodec.serverBuilder()
                .inbound(ServerboundCustomPayloadPacket.class, new ServerboundBridgePayloadHandler(this))
                .build())
            .build();
    }

    @Override
    public void onEnable() {
        // built-in feature source: PearlDrop empty chambers. Gated inside the supplier so a config toggle takes
        // effect live (the next sync will carry an empty list, which clears the group on the client).
        registerWaypointSource(GROUP_PEARLDROP_EMPTIES, this::pearlDropEmpties);
    }

    @Override
    public void onDisable() {
        modPresent.clear();
        channelRegistered.clear();
        lastPublished.clear();
        tickCounter = 0;
    }

    // ---- public service API --------------------------------------------------------------------

    /** Register a handler for an inbound (client -&gt; proxy) topic. The reader is positioned after the topic. */
    public void registerInboundHandler(String topic, BiConsumer<ServerSession, BridgeProtocol.Reader> handler) {
        inboundHandlers.put(topic, handler);
    }

    /** Register a live source of waypoints for {@code group}; polled each throttled tick and synced on change. */
    public void registerWaypointSource(String group, Supplier<List<BridgeWaypoint>> supplier) {
        waypointSources.put(group, supplier);
    }

    public void unregisterWaypointSource(String group) {
        waypointSources.remove(group);
        lastPublished.remove(group);
        broadcast(BridgeProtocol.encodeWaypointClear(group));
    }

    /** Push a full-group replace to all mod-present connections immediately. */
    public void publishGroup(String group, List<BridgeWaypoint> waypoints) {
        lastPublished.put(group, List.copyOf(waypoints));
        broadcast(BridgeProtocol.encodeWaypointSync(group, waypoints));
    }

    public boolean isModPresent(ServerSession session) {
        return modPresent.contains(session.getUUID());
    }

    public int modConnectionCount() {
        return modPresent.size();
    }

    public List<String> registeredGroups() {
        return new ArrayList<>(waypointSources.keySet());
    }

    // ---- inbound -------------------------------------------------------------------------------

    /** Entry point from {@link ServerboundBridgePayloadHandler}; decodes the envelope and dispatches by topic. */
    public void handleInbound(ServerSession session, byte[] data) {
        try {
            BridgeProtocol.Reader r = new BridgeProtocol.Reader(data);
            r.readVarInt(); // protocol version (reserved for future negotiation)
            String topic = r.readString();
            var handler = inboundHandlers.get(topic);
            if (handler != null) handler.accept(session, r);
            else debug("No inbound handler for bridge topic '" + topic + "'");
        } catch (Exception e) {
            warn("Failed to handle inbound bridge message from " + session.getName(), e);
        }
    }

    public void markModPresent(ServerSession session) {
        if (modPresent.add(session.getUUID())) {
            info("ProxyBridge detected on connection {}", session.getName());
            sendChannelRegister(session);
            sendHello(session);
            republishAllTo(session);
        }
    }

    /**
     * Announce {@code proxybridge:main} to the client via a clientbound {@code minecraft:register}. Without this the
     * mod's {@code ClientPlayNetworking.canSend(...)} stays false — so it reports "channel not ready" and its send
     * path (swap / pull / cmd) silently no-ops. The mod only ever announces the channel <i>to</i> us; the proxy has
     * to announce it back. Sent once on mod detection, when the connection is already in PLAY. Vanilla clients
     * ignore an unknown channel here, so it's harmless to non-modded connections.
     */
    private void sendChannelRegister(ServerSession session) {
        session.sendAsync(new ClientboundCustomPayloadPacket(
            Key.key("minecraft", "register"),
            BridgeProtocol.CHANNEL_ID.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private void onHello(ServerSession session, BridgeProtocol.Reader r) {
        String side = r.readString();
        String version = r.readString();
        int featureCount = r.readVarInt();
        List<String> features = new ArrayList<>(featureCount);
        for (int i = 0; i < featureCount; i++) features.add(r.readString());
        info("Bridge hello from {} (side={}, version={}, features={})", session.getName(), side, version, features);
        markModPresent(session); // covers clients that don't announce via minecraft:register
    }

    private void onCmdInvoke(ServerSession session, BridgeProtocol.Reader r) {
        String name = r.readString();
        String args = r.readString();
        if (!isCommandAllowed(name)) {
            warn("Blocked disallowed bridge command '{}' from {}", name, session.getName());
            send(session, BridgeProtocol.encodeToast("Command not allowed: " + name));
            return;
        }
        String full = (args == null || args.isBlank()) ? name : name + " " + args;
        info("Bridge command from {}: {}", session.getName(), full);
        executeCommand(full, true);
    }

    private boolean isCommandAllowed(String name) {
        if (name == null || name.isBlank()) return false;
        String first = name.trim().split("\\s+")[0];
        for (String allowed : CONFIG.client.extra.bridge.invokeAllowList) {
            if (allowed.equalsIgnoreCase(first)) return true;
        }
        return false;
    }

    // ---- publishing ----------------------------------------------------------------------------

    private void onTick(ClientBotTick event) {
        // Proactively announce our channel to every active connection (once each) so the mod's canSend flips true
        // even if we never catch its minecraft:register. Vanilla clients ignore an unknown register channel.
        for (ServerSession con : Proxy.getInstance().getActiveConnections().getArray()) {
            if (channelRegistered.add(con.getUUID())) sendChannelRegister(con);
        }
        if (waypointSources.isEmpty() || modPresent.isEmpty()) return;
        int interval = Math.max(1, CONFIG.client.extra.bridge.publishIntervalTicks);
        if (++tickCounter < interval) return;
        tickCounter = 0;
        for (var entry : waypointSources.entrySet()) {
            String group = entry.getKey();
            List<BridgeWaypoint> current;
            try {
                current = entry.getValue().get();
            } catch (Exception e) {
                warn("Waypoint source '{}' threw", group, e);
                continue;
            }
            if (current == null) current = List.of();
            if (current.equals(lastPublished.get(group))) continue; // unchanged
            lastPublished.put(group, List.copyOf(current));
            broadcast(BridgeProtocol.encodeWaypointSync(group, current));
        }
    }

    private void onConnectionRemoved(PlayerConnectionRemovedEvent event) {
        ServerSession session = event.serverConnection();
        if (session != null) {
            modPresent.remove(session.getUUID());
            channelRegistered.remove(session.getUUID());
        }
    }

    private void sendHello(ServerSession session) {
        List<String> features = new ArrayList<>(waypointSources.keySet());
        features.add(BridgeProtocol.TOPIC_CMD_INVOKE);
        send(session, BridgeProtocol.encodeHello(SIDE, VERSION, features));
    }

    private void republishAllTo(ServerSession session) {
        for (var entry : waypointSources.entrySet()) {
            List<BridgeWaypoint> current;
            try {
                current = entry.getValue().get();
            } catch (Exception e) {
                continue;
            }
            if (current == null) current = List.of();
            send(session, BridgeProtocol.encodeWaypointSync(entry.getKey(), current));
        }
    }

    private void broadcast(byte[] payload) {
        boolean spectators = CONFIG.client.extra.bridge.broadcastToSpectators;
        ServerSession[] connections = Proxy.getInstance().getActiveConnections().getArray();
        for (ServerSession con : connections) {
            if (!isModPresent(con)) continue;
            if (!spectators && con.isSpectator()) continue;
            con.sendAsync(new ClientboundCustomPayloadPacket(BRIDGE_CHANNEL, payload));
        }
    }

    private void send(ServerSession session, byte[] payload) {
        session.sendAsync(new ClientboundCustomPayloadPacket(BRIDGE_CHANNEL, payload));
    }

    // ---- built-in PearlDrop source -------------------------------------------------------------

    private List<BridgeWaypoint> pearlDropEmpties() {
        if (!CONFIG.client.extra.bridge.autoPublishPearlDrop) return List.of();
        PearlDrop pd = MODULE.get(PearlDrop.class);
        if (pd == null || !pd.isEnabled()) return List.of();
        DimensionData dim = CACHE.getChunkCache().getCurrentDimension();
        String dimName = dim != null ? dim.name() : "minecraft:overworld";
        List<BridgeWaypoint> out = new ArrayList<>();
        for (PearlDrop.StasisChamber c : pd.getLastScan()) {
            if (pd.isOccupied(c) || !pd.hasRim(c)) continue;
            String id = "pd_" + c.columnX() + "_" + c.soulSandY() + "_" + c.columnZ();
            out.add(new BridgeWaypoint(
                id, "Pearl", dimName,
                c.columnX(), c.soulSandY() + 1, c.columnZ(),
                BridgeWaypoint.COLOR_GREEN, 0));
        }
        return out;
    }
}
