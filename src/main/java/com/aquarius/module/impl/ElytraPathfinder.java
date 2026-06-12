package com.aquarius.module.impl;

import com.aquarius.feature.player.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Coarse-grid 3D A* over the loaded chunk cache — the look-ahead that lets {@link ElytraPilot} route THROUGH
 * open nether (and around pockets / walls / lava) instead of reacting block-by-block and wandering into a dead end.
 *
 * <p>Route QUALITY rules (each one earned by a live incident where the bot was led into a pocket and died):
 * <ul>
 *   <li><b>Whole-cell sampling</b> — a cell is open only if several columns across the 4×4×4 box are flyable,
 *       not just the single centre column (which let routes thread cells that were mostly terrain/fire/lava).</li>
 *   <li><b>Confinement cost</b> — cells hugging terrain cost extra, so paths prefer the middle of open volumes
 *       and only thread tight gaps when there is no alternative.</li>
 *   <li><b>Pocket-aware partial endpoint</b> — when the goal is out of reach, the leg's destination is the best
 *       cell toward it that is also OPEN; "geometrically nearest the goal" used to regularly be inside a pit.</li>
 *   <li><b>Blind high-band routing</b> — unloaded cells are passable at a cost, but only at y {@value #BLIND_MIN_Y}..
 *       {@value #BLIND_MAX_Y} (above most terrain, below the bedrock roof): beyond the frontier the route aims
 *       high through unseen space instead of dead-ending in the last loaded pit. The chunk-frontier governor
 *       keeps the bot from outrunning what it can see; this just gives the leg a sane DIRECTION.</li>
 * </ul>
 *
 * <p>Pure Java, no native deps. Full-route planning through ungenerated terrain is still the heavier native
 * babbaj/nether-pathfinder upgrade, deliberately not taken here.
 */
public final class ElytraPathfinder {
    private ElytraPathfinder() {}

    private static final int CELL = 4;                 // grid cell size in blocks
    private static final byte OPEN = 0, BLOCKED = 1, UNLOADED = 2;
    private static final int BLIND_MIN_Y = 80, BLIND_MAX_Y = 118;
    private static final double BLIND_COST = 2.5;      // step-cost multiplier through unseen (high-band) cells
    private static final double CONFINE_COST = 0.35;   // extra step cost per blocked face neighbour
    private static final int[][] SAMPLE_COLS = {{1, 1}, {0, 0}, {3, 3}}; // columns sampled per cell (centre + diagonal corners)
    private static final int[][] FACES = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
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
     * Returns {@code null} only if the start cell itself is blocked.
     *
     * @param maxNodes      node-expansion budget (cost guard)
     * @param maxCellRadius search-box half-extent in cells around the start
     */
    public static List<int[]> findPath(int sx, int sy, int sz, int tx, int ty, int tz, int maxNodes, int maxCellRadius) {
        final int scx = cell(sx), scy = cell(sy), scz = cell(sz);
        final int gcx = cell(tx), gcy = cell(ty), gcz = cell(tz);
        final long start = key(scx, scy, scz);
        final long goal = key(gcx, gcy, gcz);

        final HashMap<Long, Byte> cache = new HashMap<>();
        if (cellState(scx, scy, scz, cache) == BLOCKED) return null; // inside a wall — nothing to follow

        final HashMap<Long, Long> cameFrom = new HashMap<>();
        final HashMap<Long, Double> g = new HashMap<>();
        final HashSet<Long> closed = new HashSet<>();
        final PriorityQueue<long[]> open = new PriorityQueue<>(
            (a, b) -> Double.compare(Double.longBitsToDouble(a[1]), Double.longBitsToDouble(b[1])));

        g.put(start, 0.0);
        open.add(new long[]{start, Double.doubleToLongBits(heur(scx, scy, scz, gcx, gcy, gcz))});
        long best = start;
        double bestScore = heur(scx, scy, scz, gcx, gcy, gcz);
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
                if (closed.contains(nk)) continue;
                final byte st = cellState(nx, ny, nz, cache);
                if (st == BLOCKED) continue;
                double step = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                int conf = 0;
                if (st == UNLOADED) {
                    final int by = ny * CELL;
                    if (by < BLIND_MIN_Y || by > BLIND_MAX_Y) continue; // blind routing only in the high band
                    step *= BLIND_COST;
                } else {
                    conf = blockedFaces(nx, ny, nz, cache);
                    step *= 1.0 + CONFINE_COST * conf;  // hug nothing: prefer the middle of open volumes
                }
                final double ng = cg + step;
                if (ng < g.getOrDefault(nk, Double.MAX_VALUE)) {
                    g.put(nk, ng);
                    cameFrom.put(nk, cur);
                    final double nh = heur(nx, ny, nz, gcx, gcy, gcz);
                    open.add(new long[]{nk, Double.doubleToLongBits(ng + nh)});
                    // Partial-endpoint preference: toward the goal AND somewhere open. A confined cell (pocket,
                    // dead end) must never become the leg's destination just for being nearest the goal.
                    final double endScore = nh + (st == UNLOADED ? 1.0 : 1.5 * conf);
                    if (endScore < bestScore) { bestScore = endScore; best = nk; }
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

    /** Blocked face-neighbours of a cell (6-connected) — its "confinement". Unloaded neighbours count as open. */
    private static int blockedFaces(int cx, int cy, int cz, HashMap<Long, Byte> cache) {
        int n = 0;
        for (int[] f : FACES)
            if (cellState(cx + f[0], cy + f[1], cz + f[2], cache) == BLOCKED) n++;
        return n;
    }

    /**
     * Cell state, cached. OPEN requires every sampled column ({@link #SAMPLE_COLS}) of the 4×4×4 box to be
     * air/water — solid, lava, and fire all block (lava/fire are not air). The old single-centre-column sample
     * let routes thread cells that were mostly terrain or burning.
     */
    private static byte cellState(int cx, int cy, int cz, HashMap<Long, Byte> cache) {
        final long k = key(cx, cy, cz);
        final Byte cached = cache.get(k);
        if (cached != null) return cached;
        byte st = OPEN;
        final int x0 = cx * CELL, y0 = cy * CELL, z0 = cz * CELL;
        if (!World.isChunkLoadedBlockPos(x0 + CELL / 2, z0 + CELL / 2)) {
            st = UNLOADED;
        } else {
            outer:
            for (int[] c : SAMPLE_COLS) {
                final int bx = x0 + c[0], bz = z0 + c[1];
                for (int dy = 0; dy < CELL; dy++) {
                    var b = World.getBlock(bx, y0 + dy, bz);
                    if (!b.isAir() && !World.isWater(b)) { st = BLOCKED; break outer; }
                }
            }
        }
        cache.put(k, st);
        return st;
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
