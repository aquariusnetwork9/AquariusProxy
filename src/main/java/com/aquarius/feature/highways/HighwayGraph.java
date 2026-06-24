package com.aquarius.feature.highways;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/**
 * A navigable graph over the {@link HighwayNetwork}'s radials and ring roads, for routing <em>around</em> a griefed
 * stretch instead of rerouting blindly. This is the "ring-road re-route" layer: when a section of a cardinal or
 * diagonal is impassable, A* finds a detour that hops onto the nearest ring, runs along it to a parallel radial, and
 * rejoins past the grief.
 *
 * <p>The graph is a spider-web: nodes are the points where the 8 radials (4 cardinals + 4 diagonals) cross each ring
 * road; edges are the radial arcs between consecutive rings and the ring arcs between adjacent crossings. It is pure
 * 2D planning (nether XZ) and holds no flight state — the executor follows the returned waypoints at the road Y.
 *
 * <p>Grief is supplied per-query as an {@link ArcFilter} (e.g. from {@link GriefMap} or a live look-ahead probe), so
 * the same graph re-plans against changing knowledge. Blocked arcs are heavily penalised, not removed, so a route is
 * still returned (flagged {@link Route#usedBlocked()}) when every option is compromised — better a least-bad path
 * than none.
 */
public final class HighwayGraph {

    /** Cost multiplier applied to an arc the filter marks blocked. High enough to detour, finite so A* never fails. */
    private static final double BLOCKED_PENALTY = 1_000_000.0;

    private static volatile HighwayGraph instance;

    private final Map<Long, int[]> nodes = new HashMap<>();          // packed key -> {x, z}
    private final Map<Long, Set<Long>> adj = new HashMap<>();        // packed key -> neighbour keys

    private HighwayGraph() {}

    /** Lazily builds and caches the graph from the bundled {@link HighwayNetwork}. Thread-safe. */
    public static HighwayGraph get() {
        HighwayGraph local = instance;
        if (local == null) {
            synchronized (HighwayGraph.class) {
                local = instance;
                if (local == null) {
                    instance = local = build();
                }
            }
        }
        return local;
    }

    private static HighwayGraph build() {
        HighwayGraph g = new HighwayGraph();
        TreeSet<Integer> radii = new TreeSet<>();
        for (Highway h : HighwayNetwork.get().roads()) {
            if ("ring".equals(h.category()) && h.radius() != null) {
                radii.add(h.radius());
            }
        }
        g.addNode(0, 0);
        // 8 radials: 4 cardinals (axis-aligned) + 4 diagonals (corners). A node at radius r sits at dir*r.
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] d : dirs) {
            int px = 0, pz = 0;
            for (int r : radii) {
                int x = d[0] * r, z = d[1] * r;
                g.addNode(x, z);
                g.addEdge(px, pz, x, z);
                px = x;
                pz = z;
            }
        }
        // Ring arcs: walk each ring's 8 crossings around the square; consecutive ones share an edge of the square.
        for (int r : radii) {
            int[][] ring = {{r, 0}, {r, r}, {0, r}, {-r, r}, {-r, 0}, {-r, -r}, {0, -r}, {r, -r}};
            for (int i = 0; i < 8; i++) {
                int[] a = ring[i];
                int[] b = ring[(i + 1) % 8];
                g.addEdge(a[0], a[1], b[0], b[1]);
            }
        }
        return g;
    }

    private void addNode(int x, int z) {
        long k = key(x, z);
        nodes.putIfAbsent(k, new int[] {x, z});
        adj.computeIfAbsent(k, ignored -> new LinkedHashSet<>());
    }

    private void addEdge(int x1, int z1, int x2, int z2) {
        if (x1 == x2 && z1 == z2) {
            return;
        }
        addNode(x1, z1);
        addNode(x2, z2);
        long a = key(x1, z1), b = key(x2, z2);
        adj.get(a).add(b);
        adj.get(b).add(a);
    }

    /** Total graph nodes (origin + 8 crossings per ring). */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * Routes from one nether XZ to another along the highway web, detouring around arcs the {@code filter} marks
     * blocked. Endpoints snap to the nearest graph crossing; the executor handles the short approach to the first
     * and last waypoint.
     *
     * @param filter grief/blockage source, or {@code null} for the unobstructed shortest path
     * @return the detour as a waypoint list (nether XZ), never empty
     */
    public Route route(double fromX, double fromZ, double toX, double toZ, ArcFilter filter) {
        long start = nearestNode(fromX, fromZ);
        long goal = nearestNode(toX, toZ);
        if (start == goal) {
            return new Route(List.of(nodes.get(start).clone()), 0.0, false);
        }
        Map<Long, Double> gScore = new HashMap<>();
        Map<Long, Long> came = new HashMap<>();
        gScore.put(start, 0.0);
        PriorityQueue<long[]> open = new PriorityQueue<>(Comparator.comparingDouble(n -> Double.longBitsToDouble(n[1])));
        open.add(new long[] {start, Double.doubleToLongBits(heuristic(start, goal))});
        Set<Long> closed = new java.util.HashSet<>();
        while (!open.isEmpty()) {
            long cur = open.poll()[0];
            if (cur == goal) {
                break;
            }
            if (!closed.add(cur)) {
                continue;
            }
            double gCur = gScore.get(cur);
            for (long nb : adj.getOrDefault(cur, Set.of())) {
                double cost = edgeCost(cur, nb, filter);
                double tentative = gCur + cost;
                if (tentative < gScore.getOrDefault(nb, Double.POSITIVE_INFINITY)) {
                    came.put(nb, cur);
                    gScore.put(nb, tentative);
                    double f = tentative + heuristic(nb, goal);
                    open.add(new long[] {nb, Double.doubleToLongBits(f)});
                }
            }
        }
        return reconstruct(start, goal, came, gScore, filter);
    }

    private Route reconstruct(long start, long goal, Map<Long, Long> came, Map<Long, Double> gScore, ArcFilter filter) {
        Deque<Long> chain = new ArrayDeque<>();
        long cur = goal;
        chain.addFirst(cur);
        while (cur != start) {
            Long prev = came.get(cur);
            if (prev == null) {
                // unreachable (shouldn't happen on a connected web) — fall back to the two endpoints
                return new Route(List.of(nodes.get(start).clone(), nodes.get(goal).clone()),
                    Double.POSITIVE_INFINITY, true);
            }
            cur = prev;
            chain.addFirst(cur);
        }
        List<int[]> waypoints = new ArrayList<>(chain.size());
        boolean usedBlocked = false;
        Long prev = null;
        for (long k : chain) {
            if (prev != null) {
                usedBlocked |= isBlocked(prev, k, filter);
            }
            waypoints.add(nodes.get(k).clone());
            prev = k;
        }
        return new Route(List.copyOf(waypoints), gScore.getOrDefault(goal, Double.POSITIVE_INFINITY), usedBlocked);
    }

    private double edgeCost(long a, long b, ArcFilter filter) {
        int[] pa = nodes.get(a), pb = nodes.get(b);
        double len = Math.hypot((double) pa[0] - pb[0], (double) pa[1] - pb[1]);
        return isBlocked(a, b, filter) ? len * BLOCKED_PENALTY : len;
    }

    private boolean isBlocked(long a, long b, ArcFilter filter) {
        if (filter == null) {
            return false;
        }
        int[] pa = nodes.get(a), pb = nodes.get(b);
        return filter.blocked(pa[0], pa[1], pb[0], pb[1]);
    }

    private double heuristic(long node, long goal) {
        int[] p = nodes.get(node), q = nodes.get(goal);
        return Math.hypot((double) p[0] - q[0], (double) p[1] - q[1]);
    }

    private long nearestNode(double x, double z) {
        long best = 0L;
        double bestD = Double.POSITIVE_INFINITY;
        for (Map.Entry<Long, int[]> e : nodes.entrySet()) {
            int[] p = e.getValue();
            double d = Math.hypot(x - p[0], z - p[1]);
            if (d < bestD) {
                bestD = d;
                best = e.getKey();
            }
        }
        return best;
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFF_FFFFL);
    }

    /** Decides whether a graph arc (a straight run between two crossings) is impassable / to be avoided. */
    @FunctionalInterface
    public interface ArcFilter {
        boolean blocked(int x1, int z1, int x2, int z2);
    }

    /**
     * A planned detour.
     *
     * @param waypoints  nether XZ crossings to fly in order ({@code int[]{x, z}}), start first
     * @param cost       total weighted path cost (penalised arcs inflate this)
     * @param usedBlocked true if no clean route existed and the path traverses a blocked arc anyway
     */
    public record Route(List<int[]> waypoints, double cost, boolean usedBlocked) {}
}
