package com.aquarius.feature.highways;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A bot's own, <strong>local</strong> record of highway hazards it has run into — holes, walls, wither craters,
 * exposed lava — so the {@link HighwayGraph} re-router can avoid known-bad arcs and the flight loop can pre-arm
 * caution before it re-discovers them.
 *
 * <p><strong>Privacy by design.</strong> This is a single bot's observations held in memory; it opens no
 * connections and joins no network. Recording is meant to be gated off by default at the call site, and the only way
 * data leaves is {@link #exportJson()} — a pull a separate, opt-in component (the owner's controller, a script) may
 * choose to call. Nothing here pushes anywhere. A 1–2 bot owner gets the full local re-route benefit with zero
 * network exposure; fleet sharing is a door they can open elsewhere, never a default.
 */
public final class GriefMap {

    /** New observations within this many blocks of an existing same-type hazard fold into it instead of duplicating. */
    private static final double MERGE_RADIUS = 8.0;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** What made a stretch of highway impassable. */
    public enum Type { HOLE, WALL, CRATER, LAVA }

    /**
     * One observed hazard, centred at a nether XZ with a vertical span in the highway band.
     *
     * @param dimension Minecraft dimension key (e.g. {@code minecraft:the_nether})
     * @param x         hazard centre X
     * @param z         hazard centre Z
     * @param yLo       lowest affected Y
     * @param yHi       highest affected Y
     * @param type      hazard kind
     * @param firstSeen epoch millis first observed
     * @param lastSeen  epoch millis most recently observed
     * @param hitCount  times observed (confidence)
     */
    public record Hazard(String dimension, int x, int z, int yLo, int yHi, Type type,
                         long firstSeen, long lastSeen, int hitCount) {}

    private final List<Hazard> hazards = new ArrayList<>();

    /**
     * Records a hazard, merging into a nearby same-dimension, same-type entry when one exists (bumps its hit count,
     * refreshes {@code lastSeen}, widens the Y span).
     */
    public synchronized void record(String dimension, int x, int z, int yLo, int yHi, Type type, long now) {
        for (int i = 0; i < hazards.size(); i++) {
            Hazard h = hazards.get(i);
            if (h.type == type && h.dimension.equals(dimension)
                && Math.hypot((double) h.x - x, (double) h.z - z) <= MERGE_RADIUS) {
                hazards.set(i, new Hazard(dimension, h.x, h.z,
                    Math.min(h.yLo, yLo), Math.max(h.yHi, yHi), type,
                    h.firstSeen, Math.max(h.lastSeen, now), h.hitCount + 1));
                return;
            }
        }
        hazards.add(new Hazard(dimension, x, z, yLo, yHi, type, now, now, 1));
    }

    /** Snapshot of all recorded hazards. */
    public synchronized List<Hazard> all() {
        return List.copyOf(hazards);
    }

    public synchronized int size() {
        return hazards.size();
    }

    /** Drops hazards not seen since {@code now - maxAgeMillis} (grief gets repaired; stale knowledge misleads). */
    public synchronized void prune(long maxAgeMillis, long now) {
        hazards.removeIf(h -> h.lastSeen < now - maxAgeMillis);
    }

    /**
     * An {@link HighwayGraph.ArcFilter} that blocks any arc passing within {@code clearance} blocks of a recorded
     * hazard in the given dimension. Snapshots the hazards so the filter is safe to use across re-plans.
     */
    public synchronized HighwayGraph.ArcFilter arcFilter(String dimension, double clearance) {
        List<Hazard> snapshot = new ArrayList<>();
        for (Hazard h : hazards) {
            if (h.dimension.equals(dimension)) {
                snapshot.add(h);
            }
        }
        return (x1, z1, x2, z2) -> {
            Highway.Segment seg = new Highway.Segment(x1, z1, x2, z2);
            for (Hazard h : snapshot) {
                if (seg.distanceTo(h.x, h.z) <= clearance) {
                    return true;
                }
            }
            return false;
        };
    }

    /** Serialises the map to JSON for an opt-in external pull. Does not transmit anything. */
    public synchronized String exportJson() {
        List<Map<String, Object>> out = new ArrayList<>(hazards.size());
        for (Hazard h : hazards) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dimension", h.dimension);
            m.put("x", h.x);
            m.put("z", h.z);
            m.put("yLo", h.yLo);
            m.put("yHi", h.yHi);
            m.put("type", h.type.name());
            m.put("firstSeen", h.firstSeen);
            m.put("lastSeen", h.lastSeen);
            m.put("hitCount", h.hitCount);
            out.add(m);
        }
        return MAPPER.writeValueAsString(out);
    }

    /** Reads back a map produced by {@link #exportJson()} (round-trips for handoff/testing). */
    public static GriefMap fromJson(String json) {
        GriefMap g = new GriefMap();
        JsonNode root = MAPPER.readTree(json);
        for (JsonNode n : root) {
            g.hazards.add(new Hazard(
                n.path("dimension").asString(),
                n.path("x").asInt(), n.path("z").asInt(),
                n.path("yLo").asInt(), n.path("yHi").asInt(),
                Type.valueOf(n.path("type").asString()),
                n.path("firstSeen").asLong(), n.path("lastSeen").asLong(),
                n.path("hitCount").asInt()));
        }
        return g;
    }
}
