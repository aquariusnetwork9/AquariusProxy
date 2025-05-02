package com.zenith.feature.pathfinder.process;

import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.cache.data.entity.EntityPlayer;
import com.zenith.feature.pathfinder.Baritone;
import com.zenith.feature.pathfinder.PathingCommand;
import com.zenith.feature.pathfinder.PathingCommandType;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.feature.pathfinder.goals.Goal;
import com.zenith.feature.pathfinder.goals.GoalComposite;
import com.zenith.feature.pathfinder.goals.GoalNear;
import com.zenith.util.timer.Timer;
import com.zenith.util.timer.Timers;
import lombok.Data;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.function.Predicate;

import static com.zenith.Globals.*;

/**
 * Follow an entity or set of entities
 */
public final class FollowProcess extends BaritoneProcessHelper implements IBaritoneProcess {

    private final Timer cooldownTimer = Timers.tickTimer();
    private @Nullable FollowTarget followTarget;
    private @Nullable PathingRequestFuture future;

    public FollowProcess(Baritone baritone) {
        super(baritone);
    }

    public PathingRequestFuture follow(Predicate<EntityLiving> filter) {
        PATH_LOG.info("Following entity predicate");
        var followTarget = new EntityPredicateTarget(filter);
        return follow(followTarget);
    }

    public PathingRequestFuture follow(EntityLiving entity) {
        if (entity instanceof EntityPlayer player) {
            var playerName = CACHE.getTabListCache().get(player.getUuid())
                .map(PlayerListEntry::getName)
                .orElse("Player ["+player.getUuid() + "]");
            PATH_LOG.info("Following player: {}", playerName);
        } else {
            PATH_LOG.info("Following entity: {}", entity);
        }
        var followTarget = new SingleEntityTarget(entity);
        return follow(followTarget);
    }

    public PathingRequestFuture follow(FollowTarget target) {
        onLostControl();
        this.followTarget = target;
        this.future = new PathingRequestFuture();
        return this.future;
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
        if (future != null && !future.isCompleted()) {
            future.complete(true);
            future.notifyListeners();
        }
        followTarget = null;
        future = null;
    }

    @Override
    public String displayName0() {
        return "Following " + followTarget;
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
