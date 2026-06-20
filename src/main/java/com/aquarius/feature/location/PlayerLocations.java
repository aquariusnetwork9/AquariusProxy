package com.aquarius.feature.location;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Out-of-band player-position telemetry. A player's OWN client (a separate Minecraft session the bot can't see)
 * POSTs its live position to the proxy's RBAC HTTP API ({@code POST /position}, token-authenticated, so the position
 * is attributed to the caller's UUID). {@link com.aquarius.module.impl.WhisperControl} reads it so {@code come} /
 * {@code follow} can target a commander who is outside the bot's render distance.
 *
 * <p>This is the only mechanism that works <i>cross-session</i>: a Minecraft plugin channel (ProxyBridge) can only
 * reach a client connected through the proxy, which is itself bounded to the bot's loaded world — an independent
 * client on its own server connection has to report out-of-band over HTTP.
 */
public final class PlayerLocations {

    /** A reported position. {@code dimension} is the resource path with no namespace, e.g. "overworld" / "the_nether". */
    public record Pos(double x, double y, double z, String dimension, long timestampMs) {}

    private static final Map<UUID, Pos> POSITIONS = new ConcurrentHashMap<>();

    private PlayerLocations() {}

    public static void report(UUID uuid, double x, double y, double z, String dimension) {
        if (uuid == null) return;
        String dim = dimension == null ? ""
            : (dimension.contains(":") ? dimension.substring(dimension.indexOf(':') + 1) : dimension).toLowerCase();
        POSITIONS.put(uuid, new Pos(x, y, z, dim, System.currentTimeMillis()));
    }

    /** Latest reported position for {@code uuid}, if present and newer than {@code maxAgeMs} (0 = no age limit). */
    public static Optional<Pos> get(UUID uuid, long maxAgeMs) {
        if (uuid == null) return Optional.empty();
        Pos p = POSITIONS.get(uuid);
        if (p == null) return Optional.empty();
        if (maxAgeMs > 0 && System.currentTimeMillis() - p.timestampMs() > maxAgeMs) return Optional.empty();
        return Optional.of(p);
    }

    public static void clear(UUID uuid) { if (uuid != null) POSITIONS.remove(uuid); }
}
