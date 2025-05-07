package com.zenith.feature.coordobf;

import com.zenith.network.server.ServerSession;
import com.zenith.util.math.MutableVec3d;
import lombok.Data;
import org.jspecify.annotations.NullMarked;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.zenith.Globals.CACHE;

@NullMarked
@Data
public class ObfPlayerState {
    private final ServerSession session;
    // player position in cache is either not relevant (this player is a spectator) or desynced with zenith client tick loop
    // this position shouldn't be trusted as its entirely controlled by players, but is useful for tracking their relative movement
    private final MutableVec3d playerPos = new MutableVec3d(CACHE.getPlayerCache().getX(), CACHE.getPlayerCache().getY(), CACHE.getPlayerCache().getZ());
    private CoordOffset coordOffset = new CoordOffset(0, 0);
    private boolean respawning = false;
    private boolean isInGame = false; // player has accepted the join game packet and spawned at correct position
    private final Queue<ServerTeleport> serverTeleports = new LinkedBlockingQueue<>();
    public record ServerTeleport(double x, double y, double z, int id) {}
}
