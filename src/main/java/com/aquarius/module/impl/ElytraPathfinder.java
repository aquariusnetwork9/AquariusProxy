package com.aquarius.module.impl;

import com.aquarius.feature.player.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Tiny coarse-grid 3D A* over the loaded chunk cache — the look-ahead that lets {@link ElytraPilot} route THROUGH
 * open space (and around pockets / walls / lava / griefed road sections) instead of reacting block-by-block and
 * nosing into a dead end.
 *
 * <p>It searches a {@value #CELL}-block grid (cheap), treats an <b>unloaded</b> cell as closed (so it only routes
 * through space it can actually see) and lava as solid. When it can't reach the target within the node budget /
 * loaded area, it returns the best partial route TOWARD it (the explored cell nearest the goal), so the bot always
 * makes progress and re-plans as more chunks stream in. Pure Java, no native deps.
 *
 * <p>Ported from the elytra-bounce-kava branch. Limit: loaded chunks only — it plans the next ~hundred blocks and is
 * re-run continuously, rather than solving a whole cross-nether route up front. Full-route planning through
 * UNGENERATED terrain is the heavier native babbaj/nether-pathfinder upgrade ({@link NetherRouter}).
 */
public final class ElytraPathfinder {
    private ElytraPathfinder() {}

    private static final int CELL = 4;                 // grid cell size in blocks
    private static final int[][] NEIGHBOURS;           // 26-connected
    static {
        List<int[]> n = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    if (dx != 0 || dy != 0 || dz != 0) n.add(new int[]{dx, dy, dz});
        NEIGHBOURS = n.toArray(new int[0][]);
    }

    /**
     * A* on the {@value #CELL}-block grid from the start block toward (tx,ty,tz). Returns block-center waypoints
     * (forward order, start first) toward the goal — the full path if reached, else the best partial toward it.
     * Returns {@code null} only if the start cell itself is blocked/unseen.
     *
     * @param maxNodes      node-expansion budget (cost guard)
     * @param maxCellRadius search-box half-extent in cells around the start
     */
    public static List<int[]> findPath(int sx, int sy, int sz, int tx, int ty, int tz, int maxNodes, int maxCellRadius) {
        final int scx = cell(sx), scy = cell(sy), scz = cell(sz);
        final int gcx = cell(tx), gcy = cell(ty), gcz = cell(tz);
        final long start = key(scx, scy, scz);
        final long goal = key(gcx, gcy, gcz);

        final HashMap<Long, Boolean> openCache = new HashMap<>();
        if (!cellOpen(scx, scy, scz, openCache)) return null; // we're inside a wall/unseen — nothing to follow

        final HashMap<Long, Long> cameFrom = new HashMap<>();
        final HashMap<Long, Double> g = new HashMap<>();
        final HashSet<Long> closed = new HashSet<>();
        final PriorityQueue<long[]> open = new PriorityQueue<>(
            (a, b) -> Double.compare(Double.longBitsToDouble(a[1]), Double.longBitsToDouble(b[1])));

        g.put(start, 0.0);
        open.add(new long[]{start, Double.doubleToLongBits(heur(scx, scy, scz, gcx, gcy, gcz))});
        long best = start;
        double bestH = heur(scx, scy, scz, gcx, gcy, gcz);
        int expanded = 0;

        while (!open.isEmpty() && expanded < maxNodes) {
            final long cur = open.poll()[0];
            if (!closed.add(cur)) continue;            // already expanded with a better/equal cost
            if (cur == goal) { best = goal; break; }
            expanded++;
            final int cx = ux(cur), cy = uy(cur), cz = uz(cur);
            final double cg = g.getOrDefault(cur, Double.MAX_VALUE);
            for (int[] d : NEIGHBOURS) {
                final int nx = cx + d[0], ny = cy + d[1], nz = cz + d[2];
                if (Math.abs(nx - scx) > maxCellRadius || Math.abs(ny - scy) > maxCellRadius
                        || Math.abs(nz - scz) > maxCellRadius) continue;
                final long nk = key(nx, ny, nz);
                if (closed.contains(nk) || !cellOpen(nx, ny, nz, openCache)) continue;
                final double ng = cg + Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                if (ng < g.getOrDefault(nk, Double.MAX_VALUE)) {
                    g.put(nk, ng);
                    cameFrom.put(nk, cur);
                    final double nh = heur(nx, ny, nz, gcx, gcy, gcz);
                    open.add(new long[]{nk, Double.doubleToLongBits(ng + nh)});
                    if (nh < bestH) { bestH = nh; best = nk; }
                }
            }
        }

        final ArrayList<int[]> path = new ArrayList<>();
        long node = best;
        path.add(center(node));
        while (node != start) {
            final Long parent = cameFrom.get(node);
            if (parent == null) break;
            node = parent;
            path.add(center(node));
        }
        Collections.reverse(path);
        return path;
    }

    /** A cell is open if its full center column ({@value #CELL} blocks) is air/water (not solid, not lava) and loaded. */
    private static boolean cellOpen(int cx, int cy, int cz, HashMap<Long, Boolean> cache) {
        final long k = key(cx, cy, cz);
        final Boolean cached = cache.get(k);
        if (cached != null) return cached;
        boolean open = true;
        final int bx = cx * CELL + CELL / 2, by = cy * CELL, bz = cz * CELL + CELL / 2;
        if (!World.isChunkLoadedChunkPos(bx >> 4, bz >> 4)) {
            open = false;                              // unseen -> don't route into it
        } else {
            for (int dy = 0; dy < CELL; dy++) {
                var b = World.getBlock(bx, by + dy, bz);
                if (!b.isAir() && !World.isWater(b)) { open = false; break; } // solid or lava blocks the cell
            }
        }
        cache.put(k, open);
        return open;
    }

    private static int[] center(long k) {
        return new int[]{ ux(k) * CELL + CELL / 2, uy(k) * CELL + CELL / 2, uz(k) * CELL + CELL / 2 };
    }

    private static double heur(int cx, int cy, int cz, int gx, int gy, int gz) {
        final double dx = cx - gx, dy = cy - gy, dz = cz - gz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int cell(int b) { return Math.floorDiv(b, CELL); }

    // pack 3 signed 21-bit cell coords into a long (±1,048,575 cells = ±4.19M blocks — covers the nether)
    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (long) (z & 0x1FFFFF);
    }
    private static int ux(long k) { return sext((int) ((k >> 42) & 0x1FFFFF)); }
    private static int uy(long k) { return sext((int) ((k >> 21) & 0x1FFFFF)); }
    private static int uz(long k) { return sext((int) (k & 0x1FFFFF)); }
    private static int sext(int v) { return (v & 0x100000) != 0 ? v | ~0x1FFFFF : v; } // sign-extend 21-bit
}
