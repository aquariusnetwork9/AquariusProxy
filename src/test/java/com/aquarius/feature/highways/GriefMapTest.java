package com.aquarius.feature.highways;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the local grief store: nearby same-type observations merge, stale ones prune, the arc filter blocks arcs
 * passing near a hazard, and JSON export/import round-trips.
 */
class GriefMapTest {

    private static final String NETHER = "minecraft:the_nether";

    @Test
    void recordsAndMergesNearbyDuplicates() {
        GriefMap g = new GriefMap();
        g.record(NETHER, 15_000, 0, 118, 124, GriefMap.Type.CRATER, 1_000L);
        g.record(NETHER, 15_004, 2, 117, 124, GriefMap.Type.CRATER, 2_000L);   // within 8 blocks -> merges
        assertEquals(1, g.size());
        GriefMap.Hazard h = g.all().get(0);
        assertEquals(2, h.hitCount());
        assertEquals(2_000L, h.lastSeen());
        assertEquals(117, h.yLo(), "merge widens the Y span");

        g.record(NETHER, 15_500, 0, 118, 124, GriefMap.Type.CRATER, 3_000L);   // far -> distinct
        assertEquals(2, g.size());

        g.record(NETHER, 15_000, 0, 118, 124, GriefMap.Type.WALL, 3_000L);     // same spot, other type -> distinct
        assertEquals(3, g.size());
    }

    @Test
    void prunesStaleHazards() {
        GriefMap g = new GriefMap();
        g.record(NETHER, 0, 5_000, 118, 124, GriefMap.Type.HOLE, 1_000L);
        g.prune(1_000L, 3_000L);          // lastSeen 1000 < 3000-1000=2000 -> dropped
        assertEquals(0, g.size());
        g.record(NETHER, 0, 5_000, 118, 124, GriefMap.Type.HOLE, 5_000L);
        g.prune(1_000L, 5_200L);          // fresh -> kept
        assertEquals(1, g.size());
    }

    @Test
    void arcFilterBlocksArcsNearAHazard() {
        GriefMap g = new GriefMap();
        g.record(NETHER, 15_000, 0, 118, 124, GriefMap.Type.CRATER, 1_000L);
        HighwayGraph.ArcFilter f = g.arcFilter(NETHER, 50.0);
        assertTrue(f.blocked(10_000, 0, 20_000, 0), "arc through the crater is blocked");
        assertFalse(f.blocked(10_000, 10_000, 20_000, 10_000), "arc 10k away is clear");
        // a different dimension is unaffected
        assertFalse(g.arcFilter("minecraft:overworld", 50.0).blocked(10_000, 0, 20_000, 0));
    }

    @Test
    void exportImportRoundTrips() {
        GriefMap g = new GriefMap();
        g.record(NETHER, 15_000, 0, 118, 124, GriefMap.Type.LAVA, 1_000L);
        g.record(NETHER, -7_500, 7_500, 119, 123, GriefMap.Type.WALL, 2_000L);
        GriefMap back = GriefMap.fromJson(g.exportJson());
        assertEquals(2, back.size());
        GriefMap.Hazard h = back.all().get(0);
        assertEquals(15_000, h.x());
        assertEquals(GriefMap.Type.LAVA, h.type());
    }
}
