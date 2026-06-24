package com.aquarius.feature.highways;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the ring-road re-route planner: the web is built from the ring set, an unobstructed route runs straight down
 * the radial, and a blocked radial forces a clean detour (no blocked arc traversed). Pure planning — no Minecraft.
 */
class HighwayGraphTest {

    private static final int A_X = 10_000, B_X = 20_000;  // two crossings on the +X cardinal at rings 10k / 20k

    @Test
    void buildsWebFromRings() {
        long nRings = HighwayNetwork.get().roads().stream()
            .filter(r -> "ring".equals(r.category()) && r.radius() != null)
            .map(Highway::radius).distinct().count();
        // origin + 8 crossings (4 cardinal mids + 4 diagonal corners) per ring
        assertEquals(1 + 8 * nRings, HighwayGraph.get().nodeCount());
    }

    @Test
    void unobstructedRouteFollowsTheRadial() {
        HighwayGraph.Route r = HighwayGraph.get().route(A_X, 0, B_X, 0, null);
        assertFalse(r.usedBlocked());
        List<int[]> wp = r.waypoints();
        assertEquals(A_X, wp.get(0)[0]);
        assertEquals(0, wp.get(0)[1]);
        assertEquals(B_X, wp.get(wp.size() - 1)[0]);
        assertEquals(0, wp.get(wp.size() - 1)[1]);
        for (int[] p : wp) {
            assertEquals(0, p[1], "should stay on the z=0 cardinal");
        }
    }

    @Test
    void reroutesAroundABlockedRadial() {
        // Block the +X cardinal between rings 10k and 20k (the direct path).
        HighwayGraph.ArcFilter blockedRadial = (x1, z1, x2, z2) ->
            z1 == 0 && z2 == 0 && Math.min(x1, x2) >= A_X && Math.max(x1, x2) <= B_X;

        HighwayGraph.Route r = HighwayGraph.get().route(A_X, 0, B_X, 0, blockedRadial);
        List<int[]> wp = r.waypoints();

        assertFalse(r.usedBlocked(), "a clean ring detour exists");
        assertTrue(wp.size() > 3, "detour is longer than the 3-node direct path");
        assertEquals(A_X, wp.get(0)[0]);
        assertEquals(B_X, wp.get(wp.size() - 1)[0]);
        // no consecutive pair may be the blocked radial
        for (int i = 0; i + 1 < wp.size(); i++) {
            int[] a = wp.get(i), b = wp.get(i + 1);
            assertFalse(blockedRadial.blocked(a[0], a[1], b[0], b[1]), "detour must not use the blocked arc");
        }
    }
}
