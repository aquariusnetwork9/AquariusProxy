package com.zenith.feature.pathfinder;

import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.feature.pathfinder.goals.Goal;
import com.zenith.mc.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

public interface BaritoneAPI {
    boolean isActive();
    PathingRequestFuture pathTo(Goal goal);
    PathingRequestFuture pathTo(int x, int z);
    PathingRequestFuture pathTo(int x, int y, int z);
    PathingRequestFuture thisWay(int dist);
    PathingRequestFuture getTo(Block block);
    PathingRequestFuture mine(Block... blocks);
    PathingRequestFuture follow(Predicate<EntityLiving> entityPredicate);
    PathingRequestFuture follow(EntityLiving entity);
    PathingRequestFuture leftClickBlock(int x, int y, int z);
    PathingRequestFuture rightClickBlock(int x, int y, int z);
    PathingRequestFuture leftClickEntity(EntityLiving entity);
    PathingRequestFuture rightClickEntity(EntityLiving entity);
    void stop();
    @Nullable Goal currentGoal();
}
