package com.aquarius.feature.bridge;

/**
 * A waypoint published over the ProxyBridge channel for the client mod to render (Xaero's-style markers /
 * Meteor overlay). Coordinates are absolute block positions in {@code dimension}.
 *
 * @param id        stable identifier within a group (used for diffing/removal), e.g. {@code "pd_123_64_-200"}
 * @param name      display label, e.g. {@code "Pearl"}
 * @param dimension dimension registry id the waypoint lives in, e.g. {@code "minecraft:the_nether"}
 * @param x         block X
 * @param y         block Y
 * @param z         block Z
 * @param color     display colour as {@code 0xRRGGBB}
 * @param ttlTicks  auto-expiry in client ticks; {@code 0} = no expiry (group sync controls lifetime instead)
 */
public record BridgeWaypoint(
    String id,
    String name,
    String dimension,
    int x,
    int y,
    int z,
    int color,
    int ttlTicks
) {
    public static final int COLOR_GREEN = 0x55FF55;
    public static final int COLOR_AQUA = 0x55FFFF;
    public static final int COLOR_YELLOW = 0xFFFF55;
    public static final int COLOR_RED = 0xFF5555;
}
