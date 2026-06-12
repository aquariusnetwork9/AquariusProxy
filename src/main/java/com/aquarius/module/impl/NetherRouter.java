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

/**
 * Full-route nether planning via babbaj/nether-pathfinder — the native C++ A* Baritone's elytra uses. It
 * GENERATES nether terrain from the world seed in C++, so routes go through UNLOADED chunks: the whole leg is
 * planned up front instead of discovering lava pockets 12 chunks at a time (which killed the bot three times).
 *
 * <p>Observed chunks are fed in as they stream from the server ({@code submitChunk}) and override generation,
 * so the route self-corrects where the real world differs from the seed (post-1.12 regenerated terrain, builds).
 *
 * <p>The native context is NOT thread-safe: every native call is confined to the single "NetherRouter" daemon
 * thread. Results come back as {@link CompletableFuture}s; callers poll/complete on their own threads.
 *
 * <p>Waypoints are packed {@code x[26]<<38 | y[12]<<26 | z[26]} (verified empirically against a generated
 * route — NOT the vanilla BlockPos layout).
 */
public final class NetherRouter {

    public static final NetherRouter INSTANCE = new NetherRouter();

    /** A planned route: forward-ordered block waypoints; {@code finished} = reached the destination (vs a partial). */
    public record Route(List<int[]> points, boolean finished) { }

    private static final int MAX_HEIGHT = 128;
    private static final double FAKE_CHUNK_COST = 1.0;   // neutral: trust generation as much as observation
    private static final int CULL_DISTANCE_BLOCKS = 64000;

    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NetherRouter");
        t.setDaemon(true);
        return t;
    });

    // All mutated on the router thread only.
    private long context;
    private long contextSeed;
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
            }
        });
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
