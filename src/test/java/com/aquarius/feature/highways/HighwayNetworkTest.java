package com.aquarius.feature.highways;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the bundled nether-highway map and the snap math: that the resource loads, the headline geometry is intact
 * (cardinals through origin, the world-border extent, a known ring), and point->segment projection clamps to the
 * endpoints. Pure data + arithmetic — no Minecraft connection.
 */
class HighwayNetworkTest {

    @Test
    void loadsBundledMap() {
        HighwayNetwork net = HighwayNetwork.get();
        assertFalse(net.roads().isEmpty(), "map should contain roads");
        assertEquals(30_000_000, net.netherWorldBorder());
        assertEquals(120, net.defaultYLevel());
        // all four road kinds are present
        assertTrue(net.roads().stream().anyMatch(r -> r.category().equals("axis")));
        assertTrue(net.roads().stream().anyMatch(r -> r.category().equals("ring")));
        assertTrue(net.roads().stream().anyMatch(r -> r.category().equals("diamond")));
        assertTrue(net.roads().stream().anyMatch(r -> r.category().equals("grid")));
    }

    @Test
    void allGeometryWithinWorldBorder() {
        HighwayNetwork net = HighwayNetwork.get();
        int wb = net.netherWorldBorder();
        for (Highway road : net.roads()) {
            for (Highway.Segment s : road.segments()) {
                assertTrue(Math.abs(s.x1()) <= wb && Math.abs(s.z1()) <= wb
                        && Math.abs(s.x2()) <= wb && Math.abs(s.z2()) <= wb,
                    "segment outside world border on " + road.name());
            }
        }
    }

    @Test
    void cardinalsPassThroughOrigin() {
        SnapAssert s = snap(HighwayNetwork.get().nearest(0, 0));
        assertEquals(0.0, s.distance, 1e-6, "spawn (0,0) sits on the cardinals");
        assertEquals("axis", s.category);
    }

    @Test
    void snapsOntoRingEdge() {
        // A point exactly on the r=50000 ring's east edge (x=50000, |z|<50000), off the 5k grid lines.
        HighwayNetwork.Snap s = HighwayNetwork.get().nearestUsable(50_000, 23_456);
        assertNotNull(s);
        assertEquals("ring", s.road().category());
        assertEquals(Integer.valueOf(50_000), s.road().radius());
        assertTrue(s.distance() < 0.5, "should snap right onto the ring edge");
        assertEquals(50_000.0, s.x(), 0.5);
        assertEquals(23_456.0, s.z(), 0.5);
        assertTrue(s.road().usable());
    }

    @Test
    void segmentProjectionClampsToEndpoints() {
        Highway.Segment seg = new Highway.Segment(0, 0, 100, 0);
        // interior projection
        double[] mid = seg.closestPoint(50, 20);
        assertEquals(50.0, mid[0], 1e-9);
        assertEquals(0.0, mid[1], 1e-9);
        assertEquals(20.0, seg.distanceTo(50, 20), 1e-9);
        // before the start -> clamps to the start endpoint
        double[] before = seg.closestPoint(-10, 5);
        assertEquals(0.0, before[0], 1e-9);
        assertEquals(0.0, before[1], 1e-9);
        assertEquals(Math.hypot(10, 5), seg.distanceTo(-10, 5), 1e-9);
        // past the end -> clamps to the end endpoint
        double[] after = seg.closestPoint(200, 0);
        assertEquals(100.0, after[0], 1e-9);
        assertEquals(0.0, after[1], 1e-9);
    }

    // tiny holder so the origin test reads cleanly
    private record SnapAssert(double distance, String category) {}

    private static SnapAssert snap(HighwayNetwork.Snap s) {
        assertNotNull(s);
        return new SnapAssert(s.distance(), s.road().category());
    }
}
