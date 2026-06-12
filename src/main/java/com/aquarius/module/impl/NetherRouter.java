package com.aquarius.module.impl;

import com.aquarius.feature.player.World;
import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.PathSegment;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Full-route nether planning via babbaj/nether-pathfinder — the native C++ A* Baritone's elytra uses. It
 * GENERATES nether terrain from the world seed in C++, so routes go through UNLOADED chunks: the whole leg is
 * planned up front instead of discovering lava pockets 12 chunks at a time (which killed the bot three times).
 *
 * <p>Observed chunks are fed in as they stream from the server ({@code submitChunk}) and override generation,
 * so the route self-corrects where the real world differs from the seed (post-1.12 regenerated terrain, builds).
 *
 * <p>Threading (the Baritone model): mutations of the native cache — context create/free, chunk inserts,
 * culling, and {@code pathFind} with generation — run on the single "NetherRouter" daemon thread under the
 * WRITE lock. Raytraces ({@code isVisible}/{@code isVisibleMulti}) are read-only and may run from any thread
 * under the READ lock; the flight solver fires thousands of them per second. Results come back as
 * {@link CompletableFuture}s; callers poll/complete on their own threads.
 *
 * <p>Waypoints are packed {@code x[26]<<38 | y[12]<<26 | z[26]} (verified empirically against a generated
 * route — NOT the vanilla BlockPos layout).
 */
public final class NetherRouter {

    public static final NetherRouter INSTANCE = new NetherRouter();

    /** A planned route: forward-ordered block waypoints; {@code finished} = reached the destination (vs a partial). */
    public record Route(List<int[]> points, boolean finished) { }

    private static final int MAX_HEIGHT = 128;
    // Baritone's value: generated (unobserved) chunks cost 8x observed ones, so where the real world is known
    // the route threads through it — generated terrain is a *prediction* (post-1.12 regen + builds diverge),
    // and a route through chunks we've actually seen never gets surprise-corrected mid-flight.
    private static final double FAKE_CHUNK_COST = 8.0;
    private static final int CULL_DISTANCE_BLOCKS = 64000;

    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NetherRouter");
        t.setDaemon(true);
        return t;
    });

    // Guards the native chunk cache: router-thread mutations take the write lock; raytraces (any thread)
    // take the read lock. The context handle itself only changes under the write lock.
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private volatile long context;
    private long contextSeed;                      // router thread only
    private final LongSet fedChunks = new LongOpenHashSet();
    private int feedErrors;

    private NetherRouter() {}

    public boolean isSupported() {
        return NetherPathfinder.isThisSystemSupported();
    }

    /** Plan a route (async, on the router thread). Completes with null when the native search returns nothing. */
    public CompletableFuture<Route> requestRoute(int sx, int sy, int sz, int tx, int ty, int tz,
                                                 long seed, int timeoutMs) {
        final CompletableFuture<Route> f = new CompletableFuture<>();
        exec.execute(() -> {
            rwl.writeLock().lock();   // pathFind generates chunks into the cache = a mutation
            try {
                ensureContext(seed);
                NetherPathfinder.cullFarChunks(context, sx >> 4, sz >> 4, CULL_DISTANCE_BLOCKS);
                final PathSegment seg = NetherPathfinder.pathFind(context, sx, sy, sz, tx, ty, tz,
                    true, false, timeoutMs, false /* generate unseen chunks from the seed */, FAKE_CHUNK_COST);
                if (seg == null || seg.packed.length == 0) {
                    f.complete(null);
                    return;
                }
                final List<int[]> pts = new ArrayList<>(seg.packed.length);
                for (final long p : seg.packed) {
                    pts.add(new int[]{ (int) (p >> 38), (int) (p << 26 >> 52), (int) (p << 38 >> 38) });
                }
                f.complete(new Route(pts, seg.finished));
            } catch (final Throwable t) {
                f.completeExceptionally(t);
            } finally {
                rwl.writeLock().unlock();
            }
        });
        return f;
    }

    /**
     * Feed an observed chunk into the native cache (async; reads the chunk cache OFF the tick thread, same as
     * Baritone's path calc does). Solidity = "not air" — lava, fire, and water all count solid, which is exactly
     * what we want avoided. Safe to call repeatedly; re-feeds overwrite.
     */
    public void submitChunk(int chunkX, int chunkZ, long seed) {
        exec.execute(() -> {
            rwl.writeLock().lock();
            try {
                ensureContext(seed);
                if (World.getChunk(chunkX, chunkZ) == null) return;
                // The native lib REQUIRES a full 16*16*256 column regardless of the context's maxHeight —
                // a 128-high array throws IllegalArgumentException. That exception was silently swallowed
                // here for six releases: every insert failed and the router flew on pure seed generation
                // ("0 observed chunks fed"), blind to all player-modified terrain. y 128..255 stays air.
                final boolean[] data = new boolean[16 * 16 * 256];   // index = y<<8 | z<<4 | x (lib convention)
                final int bx0 = chunkX << 4, bz0 = chunkZ << 4;
                for (int y = 0; y < MAX_HEIGHT; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (!World.getBlock(bx0 + x, y, bz0 + z).isAir()) {
                                data[y << 8 | z << 4 | x] = true;
                            }
                        }
                    }
                }
                NetherPathfinder.insertChunkData(context, chunkX, chunkZ, data);
                fedChunks.add(((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL));
            } catch (final Throwable t) {
                // a single bad chunk must never take the router down — but never fail SILENTLY again either
                if (feedErrors++ == 0) {
                    com.aquarius.Globals.MODULE_LOG.warn("[NetherRouter] chunk feed failed (logging first only): {}", t.toString());
                }
            } finally {
                rwl.writeLock().unlock();
            }
        });
    }

    // --- raytraces against the native cache (observed chunks override seed-generated terrain; chunks the
    // --- cache has never seen count as SOLID, the conservative Baritone semantic: never trust unknown space)

    /**
     * Single raytrace; {@code true} = the line is clear. Non-blocking: returns {@code null} when the cache is
     * mid-write (a route computing / chunks inserting) or no context exists yet — callers fall back to their
     * loaded-chunk scan. Safe from the tick thread.
     */
    public Boolean tryIsVisible(double sx, double sy, double sz, double ex, double ey, double ez) {
        if (!rwl.readLock().tryLock()) return null;
        try {
            if (context == 0) return null;
            if (sx == ex && sy == ey && sz == ez) return Boolean.TRUE; // zero-length rays crash the cpp tracer
            return NetherPathfinder.isVisible(context, NetherPathfinder.CACHE_MISS_SOLID, sx, sy, sz, ex, ey, ez);
        } finally {
            rwl.readLock().unlock();
        }
    }

    /**
     * Batched raytrace, ALL semantics: {@code true} only if EVERY segment {@code src[i]->dst[i]} is clear.
     * Blocks on the read lock (a computing route can hold the write lock for seconds) — call this from the
     * flight-solver thread, never the tick thread. {@code false} when no context exists.
     */
    public boolean allClear(int count, double[] src, double[] dst) {
        rwl.readLock().lock();
        try {
            if (context == 0) return false;
            return NetherPathfinder.isVisibleMulti(context, NetherPathfinder.CACHE_MISS_SOLID, count, src, dst, false) == -1;
        } finally {
            rwl.readLock().unlock();
        }
    }

    /** Feed failures since startup (first one is logged; the rest just count). */
    public int feedErrorCount() {
        return feedErrors;
    }

    /** Chunks fed since the context was created (router-thread counter; approximate is fine for diagnostics). */
    public int fedChunkCount() {
        return fedChunks.size();
    }

    private void ensureContext(long seed) {
        if (context != 0 && contextSeed == seed) return;
        if (context != 0) {
            NetherPathfinder.freeContext(context);
            fedChunks.clear();
        }
        context = NetherPathfinder.newContext(seed, null, NetherPathfinder.DIMENSION_NETHER, MAX_HEIGHT, true);
        contextSeed = seed;
    }
}
