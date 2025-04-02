package com.zenith.feature.coordobf;

import com.zenith.feature.world.World;
import com.zenith.mc.dimension.DimensionData;
import com.zenith.network.server.ServerSession;
import com.zenith.util.math.MutableVec3d;
import lombok.Data;
import org.jspecify.annotations.NullMarked;

import static com.zenith.Shared.CACHE;

@NullMarked
@Data
public class ObfPlayerState {
    private final ServerSession session;
    private final MutableVec3d playerPos = new MutableVec3d(CACHE.getPlayerCache().getX(), CACHE.getPlayerCache().getY(), CACHE.getPlayerCache().getZ());
    private DimensionData dimension = World.getCurrentDimension();
    private CoordOffset coordOffset = new CoordOffset(0, 0);
}
