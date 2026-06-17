package com.aquarius.module.impl;

import com.aquarius.feature.player.World;
import com.aquarius.util.math.MathHelper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.PriorityQueue;

import static com.aquarius.Globals.BLOCK_DATA;

/**
 * BoatPathfinder — a bounded 2D A* over the water surface that routes a boat around land.
 *
 * <p>The grid is the XZ plane at the boat's water surface. A cell is <b>land</b> (impassable) when a solid,
 * collision-bearing block breaches the surface band {@code {ys, ys+1}} — i.e. "a land block touching the water,
 * not submerged". A cell is <b>navigable</b> when there is water to float on at the surface and nothing breaching
 * it; submerged seafloor (water on top, solid well below) is navigable, so the boat happily glides over deep water.
 * Unloaded chunks are treated optimistically as navigable — the planner only routes around <i>known</i> land, and
 * the caller re-plans as new chunks stream in.
 *
 * <p>The search runs in a square window of half-size {@code radius} centred on the boat; a distant goal is clamped
 * onto the window edge so each plan is a local leg toward the final target. If the (clamped) goal is unreachable
 * (walled off), the closest reachable water cell is returned instead, so the boat still makes progress and re-plans.
 * The returned cell path is line-of-sight simplified so the boat takes smooth diagonal runs rather than a staircase.
 *
 * <p>Pure read-only world queries — no side effects, re-entrant; each call owns its scratch arrays (sized to the
 * window, indexed by local offset, so no hash maps / boxing on the hot path).
 */
public final class BoatPathfinder {

    private BoatPathfinder() {}

    private static final double SQRT2 = Math.sqrt(2.0);
    /** Extra cost to enter a cell that sits next to land — biases routes off the coastline for clearance. */
    private static final double COAST_PENALTY = 0.7;

    // 8-connected neighbour offsets (orthogonals first, then diagonals).
    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};

    /**
     * Plan a route from the boat to (goalX, goalZ) that avoids land.
     *
     * @param startXd  boat X (world coords)
     * @param startZd  boat Z (world coords)
     * @param surfaceY block Y of the water surface the boat floats on (≈ floor of the boat's Y)
     * @param goalXd   final target X
     * @param goalZd   final target Z
     * @param radius   search window half-size in blocks
     * @return simplified waypoint cells (packed XZ, start excluded, in travel order), or {@code null} if no useful
     *         route exists (caller should fall back to steering straight at the target)
     */
    public static @Nullable LongList plan(double startXd, double startZd, int surfaceY,
                                          double goalXd, double goalZd, int radius) {
        return new Search(MathHelper.floorI(startXd), MathHelper.floorI(startZd), surfaceY, radius)
            .run(MathHelper.floorI(goalXd), MathHelper.floorI(goalZd));
    }

    /** Re-test (no caching) whether a cell is navigable water — used by the caller to spot a stale next waypoint. */
    public static boolean isNavigable(int x, int surfaceY, int z) {
        if (!World.isChunkLoadedBlockPos(x, z)) return true; // unknown: optimistic, we only route around known land
        if (hasCollision(x, surfaceY, z) || hasCollision(x, surfaceY + 1, z)) return false; // land breaching surface
        // Need water to float on at the surface. Tolerate ±1 in case the boat sits a touch high/low on the wave.
        return World.isWater(World.getBlock(x, surfaceY, z)) || World.isWater(World.getBlock(x, surfaceY - 1, z));
    }

    private static boolean hasCollision(int x, int y, int z) {
        final int stateId = World.getBlockStateId(x, y, z);
        if (stateId == 0) return false; // air / unloaded
        return !BLOCK_DATA.getCollisionBoxesFromBlockStateId(stateId).isEmpty();
    }

    /** One A* search over a fixed window, with all scratch state indexed by local offset (no hash maps). */
    private static final class Search {
        private final int sx, sz, surfaceY, radius, side, minX, minZ;
        private final double[] g;     // gScore, +inf until reached
        private final int[] cameFrom; // parent local index, -1 = none
        private final byte[] nav;     // 0 unknown, 1 navigable, 2 land
        private final boolean[] closed;

        Search(int sx, int sz, int surfaceY, int radius) {
            this.sx = sx;
            this.sz = sz;
            this.surfaceY = surfaceY;
            this.radius = radius;
            this.side = 2 * radius + 1;
            this.minX = sx - radius;
            this.minZ = sz - radius;
            int n = side * side;
            this.g = new double[n];
            Arrays.fill(g, Double.POSITIVE_INFINITY);
            this.cameFrom = new int[n];
            Arrays.fill(cameFrom, -1);
            this.nav = new byte[n];
            this.closed = new boolean[n];
        }

        @Nullable LongList run(int goalXw, int goalZw) {
            final int gx = clamp(goalXw, minX, sx + radius);
            final int gz = clamp(goalZw, minZ, sz + radius);
            final int startIdx = idx(sx, sz);
            final int goalIdx = idx(gx, gz);

            final PriorityQueue<long[]> open = new PriorityQueue<>((a, b) -> Double.compare(
                Double.longBitsToDouble(a[1]), Double.longBitsToDouble(b[1])));
            g[startIdx] = 0.0;
            open.add(node(startIdx, heuristic(sx, sz, gx, gz)));

            int bestIdx = startIdx;
            double bestH = heuristic(sx, sz, gx, gz);
            boolean reachedGoal = false;

            while (!open.isEmpty()) {
                final int cur = (int) open.poll()[0];
                if (closed[cur]) continue;
                closed[cur] = true;
                final int cx = wx(cur), cz = wz(cur);

                final double curH = heuristic(cx, cz, gx, gz);
                if (curH < bestH) { bestH = curH; bestIdx = cur; }
                if (cur == goalIdx) { reachedGoal = true; break; }

                final double cg = g[cur];
                for (int dir = 0; dir < 8; dir++) {
                    final int nx = cx + DX[dir], nz = cz + DZ[dir];
                    if (nx < minX || nx > sx + radius || nz < minZ || nz > sz + radius) continue;
                    final int ni = idx(nx, nz);
                    if (closed[ni] || !navigable(nx, nz)) continue;
                    final boolean diagonal = DX[dir] != 0 && DZ[dir] != 0;
                    // No corner-cutting: both orthogonal cells flanking the diagonal must be open, so the
                    // ~1.4-block-wide hull can't clip a land corner.
                    if (diagonal && (!navigable(cx + DX[dir], cz) || !navigable(cx, cz + DZ[dir]))) continue;
                    double step = diagonal ? SQRT2 : 1.0;
                    if (nearLand(nx, nz)) step += COAST_PENALTY;
                    final double tentative = cg + step;
                    if (tentative < g[ni]) {
                        g[ni] = tentative;
                        cameFrom[ni] = cur;
                        open.add(node(ni, tentative + heuristic(nx, nz, gx, gz)));
                    }
                }
            }

            final int endIdx = reachedGoal ? goalIdx : bestIdx;
            if (endIdx == startIdx) return null; // couldn't move off the start cell — caller steers straight
            return simplify(reconstruct(startIdx, endIdx));
        }

        private LongList reconstruct(int startIdx, int endIdx) {
            final LongArrayList rev = new LongArrayList();
            int c = endIdx;
            while (c != -1) {
                rev.add(packWorld(c));
                if (c == startIdx) break;
                c = cameFrom[c];
            }
            final LongArrayList path = new LongArrayList(rev.size());
            for (int i = rev.size() - 1; i >= 0; i--) path.add(rev.getLong(i)); // start .. end
            return path;
        }

        /** Drop intermediate cells that have clear line of sight from the last kept anchor; exclude the start cell. */
        private @Nullable LongList simplify(LongList cells) {
            if (cells.size() <= 1) return null;
            final LongArrayList kept = new LongArrayList();
            long anchor = cells.getLong(0); // the start cell — never emitted
            for (int i = 1; i < cells.size() - 1; i++) {
                if (!lineClear(anchor, cells.getLong(i + 1))) {
                    final long keep = cells.getLong(i);
                    kept.add(keep);
                    anchor = keep;
                }
            }
            kept.add(cells.getLong(cells.size() - 1)); // always keep the final cell
            return kept.isEmpty() ? null : kept;
        }

        /** Dense sampling along the segment between two cell centres; clear iff every sampled cell is navigable. */
        private boolean lineClear(long aKey, long bKey) {
            final double ax = unpackX(aKey) + 0.5, az = unpackZ(aKey) + 0.5;
            final double bx = unpackX(bKey) + 0.5, bz = unpackZ(bKey) + 0.5;
            final double dist = Math.hypot(bx - ax, bz - az);
            final int steps = Math.max(1, (int) Math.ceil(dist / 0.2));
            for (int s = 0; s <= steps; s++) {
                final double t = (double) s / steps;
                if (!navigable(MathHelper.floorI(ax + (bx - ax) * t), MathHelper.floorI(az + (bz - az) * t))) {
                    return false;
                }
            }
            return true;
        }

        /** Navigable test, cached for in-window cells; the start cell is always escapable. */
        private boolean navigable(int x, int z) {
            if (x == sx && z == sz) return true;
            if (x < minX || x > sx + radius || z < minZ || z > sz + radius) {
                return isNavigable(x, surfaceY, z); // outside the window (line-of-sight / clearance probes): no cache
            }
            final int i = idx(x, z);
            if (nav[i] != 0) return nav[i] == 1;
            final boolean ok = isNavigable(x, surfaceY, z);
            nav[i] = (byte) (ok ? 1 : 2);
            return ok;
        }

        /** True if any of the 4 orthogonal neighbours is land — keeps a one-cell buffer off the coast. */
        private boolean nearLand(int x, int z) {
            return !navigable(x + 1, z) || !navigable(x - 1, z) || !navigable(x, z + 1) || !navigable(x, z - 1);
        }

        private int idx(int x, int z) {
            return (x - minX) * side + (z - minZ);
        }

        private int wx(int i) {
            return minX + i / side;
        }

        private int wz(int i) {
            return minZ + i % side;
        }

        private long packWorld(int i) {
            return key(wx(i), wz(i));
        }

        private static long[] node(int idx, double f) {
            return new long[] { idx, Double.doubleToRawLongBits(f) };
        }
    }

    private static double heuristic(int x, int z, int gx, int gz) {
        final double dx = gx - x, dz = gz - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }
}
