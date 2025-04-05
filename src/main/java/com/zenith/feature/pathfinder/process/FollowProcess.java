package com.zenith.feature.pathfinder.process;

import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.feature.pathfinder.Baritone;
import com.zenith.feature.pathfinder.PathingCommand;
import com.zenith.feature.pathfinder.PathingCommandType;
import com.zenith.feature.pathfinder.goals.Goal;
import com.zenith.feature.pathfinder.goals.GoalComposite;
import com.zenith.feature.pathfinder.goals.GoalNear;
import com.zenith.util.Timer;
import com.zenith.util.Timers;
import lombok.Data;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.function.Predicate;

import static com.zenith.Shared.*;

/**
 * Follow an entity or set of entities
 */
public final class FollowProcess extends BaritoneProcessHelper implements IBaritoneProcess {

    private final Timer cooldownTimer = Timers.tickTimer();
    private @Nullable FollowTarget followTarget;

    public FollowProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel, final Goal currentGoal, final PathingCommand prevCommand) {
        if (calcFailed) {
            cooldownTimer.reset();
        }
        if (cooldownTimer.tick(20, false)) {
            var target = followTarget;
            if (target != null) {
                var command = target.pathingCommand();
                if (command != null) {
                    return command;
                }
            }
            onLostControl();
        }
        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    @Override
    public boolean isActive() {
        return followTarget != null;
    }

    @Override
    public void onLostControl() {
        followTarget = null;
    }

    @Override
    public String displayName0() {
        return "Following " + followTarget;
    }

    public void follow(Predicate<EntityLiving> filter) {
        PATH_LOG.info("Following entity predicate");
        this.followTarget = new EntityPredicateTarget(filter);
    }

    public void follow(EntityLiving entity) {
        PATH_LOG.info("Following entity {}", entity);
        this.followTarget = new SingleEntityTarget(entity);
    }

    /**
     * Cancels the follow behavior, this will clear the current follow target.
     */
    public void cancel() {
        onLostControl();
    }

    public interface FollowTarget {
        PathingCommand pathingCommand();
        default boolean followable(EntityLiving entity) {
            if (entity == null) {
                return false;
            }
            if (!entity.isAlive()) {
                return false;
            }
            return entity != CACHE.getPlayerCache().getThePlayer();
        }
    }

    @Data
    public static class SingleEntityTarget implements FollowTarget {
        private final int entityId;
        private final WeakReference<EntityLiving> entityRef;

        public SingleEntityTarget(@NonNull EntityLiving entity) {
            this.entityId = entity.getEntityId();
            this.entityRef = new WeakReference<>(entity);
        }

        @Override
        public PathingCommand pathingCommand() {
            EntityLiving entity = entityRef.get();
            if (entity == null) return null;
            var goal = new GoalNear(entity.blockPos(), CONFIG.client.extra.pathfinder.followRadius);
            var type = CACHE.getPlayerCache().distanceSqToSelf(entity) <= Math.pow(25, 2)
                ? PathingCommandType.REVALIDATE_GOAL_AND_PATH
                : PathingCommandType.SOFT_REPATH;
            return new PathingCommand(goal, type);
        }
    }

    public record EntityPredicateTarget(Predicate<EntityLiving> predicate) implements FollowTarget {

        @Override
        public PathingCommand pathingCommand() {
            var entities = CACHE.getEntityCache().getEntities().values().stream()
                .filter(e -> e instanceof EntityLiving)
                .map(e -> (EntityLiving) e)
                .filter(this::followable)
                .filter(predicate)
                .toList();
            if (entities.isEmpty()) return null;
            var type = entities.stream()
                .anyMatch(e -> CACHE.getPlayerCache().distanceSqToSelf(e) <= Math.pow(25, 2))
                ? PathingCommandType.REVALIDATE_GOAL_AND_PATH
                : PathingCommandType.SOFT_REPATH;
            var goals = entities.stream()
                .map(e -> new GoalNear(e.blockPos(), CONFIG.client.extra.pathfinder.followRadius))
                .toArray(Goal[]::new);
            return new PathingCommand(new GoalComposite(goals), type);
        }
    }

}
