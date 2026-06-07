package com.aquarius.feature.pathfinder;

import com.aquarius.cache.data.entity.EntityLiving;
import com.aquarius.feature.pathfinder.goals.Goal;
import com.aquarius.mc.block.Block;
import com.aquarius.mc.block.BlockPos;
import com.aquarius.mc.item.ItemData;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

public interface Pathfinder {
    boolean isActive();
    PathingRequestFuture pathTo(Goal goal);
    PathingRequestFuture pathTo(int x, int z);
    PathingRequestFuture pathTo(int x, int y, int z);
    PathingRequestFuture thisWay(int dist);
    PathingRequestFuture getTo(Block block);
    PathingRequestFuture getTo(Block block, boolean rightClickContainerOnArrival);
    PathingRequestFuture mine(Block... blocks);
    PathingRequestFuture follow(Predicate<EntityLiving> entityPredicate);
    PathingRequestFuture follow(EntityLiving entity);
    PathingRequestFuture pickup(ItemData... items);
    PathingRequestFuture pickup();
    PathingRequestFuture leftClickBlock(int x, int y, int z);
    PathingRequestFuture rightClickBlock(int x, int y, int z);
    PathingRequestFuture breakBlock(int x, int y, int z, boolean autoTool);
    PathingRequestFuture placeBlock(int x, int y, int z, ItemData placeItem);
    PathingRequestFuture leftClickEntity(EntityLiving entity);
    PathingRequestFuture rightClickEntity(EntityLiving entity);
    PathingRequestFuture clearArea(BlockPos pos1, BlockPos pos2);
    void stop();
    @Nullable Goal currentGoal();
}
