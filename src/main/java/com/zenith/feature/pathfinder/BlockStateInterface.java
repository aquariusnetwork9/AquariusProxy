package com.zenith.feature.pathfinder;

import com.zenith.feature.world.World;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockPos;

import static com.zenith.Shared.BLOCK_DATA;

public class BlockStateInterface {
    public static final BlockStateInterface INSTANCE = new BlockStateInterface();
    private BlockStateInterface() {}

    public static boolean worldContainsLoadedChunk(int blockX, int blockZ) {
        return World.isChunkLoadedBlockPos(blockX, blockZ);
    }

    public static Block getBlock(BlockPos pos) { // won't be called from the pathing thread because the pathing thread doesn't make a single blockpos pog
        return World.getBlock(pos);
    }

    public static Block getBlock(int x, int y, int z) { // won't be called from the pathing thread because the pathing thread doesn't make a single blockpos pog
        return World.getBlock(x, y, z);
    }

    public static Block getBlock(int blockStateId) { // won't be called from the pathing thread because the pathing thread doesn't make a single blockpos pog
        return World.getBlock(blockStateId);
    }

//    public static BlockState get(BlockPos pos) {
//        return World.getBlockState(pos);
//    }
//
//    public static BlockState get(int blockStateId) {
//        return World.getBlockState(blockStateId);
//    }
//
//    public static BlockState get(int x, int y, int z) { // Mickey resigned
//        return World.getBlockState(x, y, z);
//    }

    public static int getId(BlockPos pos) {
        return World.getBlockStateId(pos);
    }

    public static int getId(int x, int y, int z) { // Mickey resigned
        return World.getBlockStateId(x, y, z);
    }

    public static boolean isLoaded(int x, int z) {
        return World.isChunkLoadedBlockPos(x, z);
    }

    public static boolean isPathfindable(final int blockStateId) {
        return BLOCK_DATA.isPathfindable(blockStateId);
    }
}
