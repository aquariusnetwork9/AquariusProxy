package com.zenith.feature.pathfinder.util;

import com.zenith.cache.data.chunk.Chunk;
import com.zenith.feature.world.World;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.dimension.DimensionData;
import com.zenith.util.math.MathHelper;
import it.unimi.dsi.fastutil.ints.IntList;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.palette.Palette;

import java.util.*;
import java.util.stream.IntStream;

import static com.zenith.Globals.CACHE;

public class WorldScanner {
    public static List<BlockPos> scanCurrentViewDistance(BlockOptionalMetaLookup filter) {
        return scanChunkRadius(filter, Integer.MAX_VALUE, Integer.MIN_VALUE, CACHE.getChunkCache().getServerViewDistance());
    }

    public static List<BlockPos> scanChunkRadius(BlockOptionalMetaLookup filter, int max, int yLevelThreshold, int maxSearchRadius) {
        ArrayList<BlockPos> res = new ArrayList<>();

        if (filter.getBlockStateIds().isEmpty()) {
            return res;
        }

        int maxSearchRadiusSq = maxSearchRadius * maxSearchRadius;
        int playerChunkX = MathHelper.floorI(CACHE.getPlayerCache().getX()) >> 4;
        int playerChunkZ = MathHelper.floorI(CACHE.getPlayerCache().getZ()) >> 4;
        DimensionData dim = World.getCurrentDimension();
        int playerY = MathHelper.floorI(CACHE.getPlayerCache().getY()) - dim.minY();

        int playerYBlockStateContainerIndex = playerY >> 4;
        int[] coordinateIterationOrder = IntStream.range(0, dim.height() / 16).boxed().sorted(
            Comparator.comparingInt(y -> Math.abs(y - playerYBlockStateContainerIndex))).mapToInt(x -> x).toArray();

        int searchRadiusSq = 0;
        boolean foundWithinY = false;
        while (true) {
            boolean allUnloaded = true;
            boolean foundChunks = false;
            for (int xoff = -searchRadiusSq; xoff <= searchRadiusSq; xoff++) {
                for (int zoff = -searchRadiusSq; zoff <= searchRadiusSq; zoff++) {
                    int distance = xoff * xoff + zoff * zoff;
                    if (distance != searchRadiusSq) {
                        continue;
                    }
                    foundChunks = true;
                    int chunkX = xoff + playerChunkX;
                    int chunkZ = zoff + playerChunkZ;
                    Chunk chunk = World.getChunk(chunkX, chunkZ);
                    if (chunk == null) {
                        continue;
                    }
                    allUnloaded = false;
                    if (scanChunkInto(chunkX << 4, chunkZ << 4, dim.minY(), chunk, filter, res, max, yLevelThreshold, playerY, coordinateIterationOrder)) {
                        foundWithinY = true;
                    }
                }
            }
            if ((allUnloaded && foundChunks)
                || (res.size() >= max
                && (searchRadiusSq > maxSearchRadiusSq || (searchRadiusSq > 1 && foundWithinY)))
            ) {
                return res;
            }
            searchRadiusSq++;
        }
    }

    public static List<BlockPos> scanChunk(BlockOptionalMetaLookup filter, int chunkX, int chunkZ, int max, int yLevelThreshold) {
        if (filter.getBlockStateIds().isEmpty()) {
            return Collections.emptyList();
        }

        Chunk chunk = World.getChunk(chunkX, chunkZ);
        int playerY = MathHelper.floorI(CACHE.getPlayerCache().getY());

        if (chunk == null || chunk.getSectionsCount() == 0) {
            return Collections.emptyList();
        }

        ArrayList<BlockPos> res = new ArrayList<>();
        DimensionData dim = World.getCurrentDimension();
        scanChunkInto(chunkX << 4, chunkZ << 4, dim.minY(), chunk, filter, res, max, yLevelThreshold, playerY, IntStream.range(0, dim.height() / 16).toArray());
        return res;
    }

    private static boolean scanChunkInto(int chunkX, int chunkZ, int minY, Chunk chunk, BlockOptionalMetaLookup filter, Collection<BlockPos> result, int max, int yLevelThreshold, int playerY, int[] coordinateIterationOrder) {
        ChunkSection[] chunkInternalStorageArray = chunk.getSections();
        boolean foundWithinY = false;
        for (int y0 : coordinateIterationOrder) {
            ChunkSection section = chunkInternalStorageArray[y0];
            if (section == null) continue;
            if (section.isBlockCountEmpty()) continue;
            int yReal = y0 << 4;
            Palette palette = section.getChunkData().getPalette();
            IntList blockStateIds = filter.getBlockStateIdList();
            boolean atLeastOneInPalette = false;
            for (int bsi = 0; bsi < blockStateIds.size(); bsi++) {
                boolean inPalette = palette.contains(blockStateIds.getInt(bsi));
                if (inPalette) {
                    atLeastOneInPalette = true;
                    break;
                }
            }
            if (!atLeastOneInPalette) continue;
            for (int yy = 0; yy < 16; yy++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int state = section.getBlock(x, yy, z);
                        if (blockStateIds.contains(state)) {
                            int y = yReal | yy;
                            if (result.size() >= max) {
                                if (Math.abs(y - playerY) < yLevelThreshold) {
                                    foundWithinY = true;
                                } else {
                                    if (foundWithinY) {
                                        // have found within Y in this chunk, so don't need to consider outside Y
                                        // TODO continue iteration to one more sorted Y coordinate block
                                        return true;
                                    }
                                }
                            }
                            result.add(new BlockPos(chunkX | x, y + minY, chunkZ | z));
                        }
                    }
                }
            }
        }
        return foundWithinY;
    }
}
