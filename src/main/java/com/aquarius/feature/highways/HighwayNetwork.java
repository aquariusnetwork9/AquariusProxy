package com.aquarius.feature.highways;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The bundled 2b2t nether-highway map, loaded once from {@code highways/nether_highways.json} on the classpath.
 *
 * <p>This is reference geometry only (the same role as {@link com.aquarius.mc.block.BlockDataManager}'s bundled
 * block data): it tells navigation code where the roads are so it can pick an on-ramp and follow a known flat line
 * at constant Y, rather than solving a free 3D nether route. It performs no I/O beyond the one resource read and is
 * fully thread-safe.
 *
 * <p>Typical use: {@code HighwayNetwork.get().nearestUsable(x, z)} to snap a position onto the nearest built road,
 * then steer along {@link HighwayNetwork.Snap#segment()} at {@link #yLevelFor(Highway)}.
 */
public final class HighwayNetwork {

    /** Classpath location of the bundled map. */
    public static final String RESOURCE = "highways/nether_highways.json";

    // Self-contained mapper so loading the map never triggers Globals' app-wide static init.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static volatile HighwayNetwork instance;

    private final int netherWorldBorder;
    private final int defaultYLevel;
    private final List<Highway> roads;

    private HighwayNetwork(int netherWorldBorder, int defaultYLevel, List<Highway> roads) {
        this.netherWorldBorder = netherWorldBorder;
        this.defaultYLevel = defaultYLevel;
        this.roads = roads;
    }

    /** Lazily loads and caches the bundled map. Thread-safe (double-checked). */
    public static HighwayNetwork get() {
        HighwayNetwork local = instance;
        if (local == null) {
            synchronized (HighwayNetwork.class) {
                local = instance;
                if (local == null) {
                    instance = local = load();
                }
            }
        }
        return local;
    }

    private static HighwayNetwork load() {
        try (InputStream in = HighwayNetwork.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("nether highway map resource not found: " + RESOURCE);
            }
            JsonNode root = MAPPER.readTree(in);
            int wb = root.path("netherWorldBorder").asInt(30_000_000);
            int defY = root.path("defaultYLevel").asInt(120);
            List<Highway> roads = new ArrayList<>();
            for (JsonNode r : root.path("roads")) {
                List<Highway.Segment> segs = new ArrayList<>();
                for (JsonNode s : r.path("segments")) {
                    segs.add(new Highway.Segment(
                        s.get(0).asInt(), s.get(1).asInt(), s.get(2).asInt(), s.get(3).asInt()));
                }
                roads.add(new Highway(
                    r.path("name").asString(),
                    r.path("category").asString(),
                    r.path("surface").asString(),
                    r.hasNonNull("dim") ? r.get("dim").asString() : null,
                    r.hasNonNull("radius") ? r.get("radius").asInt() : null,
                    r.hasNonNull("yLevel") ? r.get("yLevel").asInt() : null,
                    List.copyOf(segs)));
            }
            return new HighwayNetwork(wb, defY, List.copyOf(roads));
        } catch (Exception e) {
            throw new IllegalStateException("failed to load nether highway map", e);
        }
    }

    /** Nether world-border coordinate the cardinals/diagonals reach (same in every dimension; not 8:1-scaled). */
    public int netherWorldBorder() {
        return netherWorldBorder;
    }

    /** Y to ride a road at when it didn't pin its own (the standard highway level). */
    public int defaultYLevel() {
        return defaultYLevel;
    }

    /** All roads in the map, in map order. */
    public List<Highway> roads() {
        return roads;
    }

    /** The Y a road should be ridden at: its own {@code yLevel} if the map pinned one, else {@link #defaultYLevel()}. */
    public int yLevelFor(Highway road) {
        Integer y = road.yLevel();
        return y != null ? y : defaultYLevel;
    }

    /** Nearest road segment to a nether {@code (x, z)} over every road, or {@code null} if the map is empty. */
    public Snap nearest(double x, double z) {
        return nearest(x, z, false);
    }

    /**
     * Like {@link #nearest(double, double)} but skips {@code planned} (not-yet-built) roads. Use this for routing,
     * since you can't ride a road that doesn't exist yet.
     */
    public Snap nearestUsable(double x, double z) {
        return nearest(x, z, true);
    }

    private Snap nearest(double x, double z, boolean usableOnly) {
        Snap best = null;
        for (Highway road : roads) {
            if (usableOnly && !road.usable()) {
                continue;
            }
            for (Highway.Segment seg : road.segments()) {
                double[] c = seg.closestPoint(x, z);
                double d = Math.hypot(x - c[0], z - c[1]);
                if (best == null || d < best.distance()) {
                    best = new Snap(road, seg, c[0], c[1], d);
                }
            }
        }
        return best;
    }

    /**
     * Result of snapping a point onto the network.
     *
     * @param road     the road the nearest segment belongs to
     * @param segment  the nearest segment
     * @param x        on-road point X (the place to aim for to get on this road)
     * @param z        on-road point Z
     * @param distance straight-line XZ distance from the queried point to {@code (x, z)}
     */
    public record Snap(Highway road, Highway.Segment segment, double x, double z, double distance) {}
}
