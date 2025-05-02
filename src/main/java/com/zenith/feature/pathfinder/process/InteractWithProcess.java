package com.zenith.feature.pathfinder.process;

import com.zenith.cache.data.entity.EntityLiving;
import com.zenith.feature.pathfinder.Baritone;
import com.zenith.feature.pathfinder.PathingCommand;
import com.zenith.feature.pathfinder.PathingCommandType;
import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.feature.pathfinder.goals.Goal;
import com.zenith.feature.pathfinder.goals.GoalNear;
import com.zenith.feature.player.*;
import com.zenith.feature.player.raycast.RaycastHelper;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.LocalizedCollisionBox;
import lombok.Data;
import org.cloudburstmc.math.vector.Vector2f;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;

import static com.zenith.Globals.*;

public class InteractWithProcess extends BaritoneProcessHelper {

    private @Nullable PathingRequestFuture future;
    private @Nullable InteractTarget target = null;

    public InteractWithProcess(final Baritone baritone) {
        super(baritone);
    }

    public PathingRequestFuture rightClickBlock(int x, int y, int z) {
        return interact(new InteractWithBlock(x, y, z, false));
    }

    public PathingRequestFuture leftClickBlock(int x, int y, int z) {
        return interact(new InteractWithBlock(x, y, z, true));
    }

    public PathingRequestFuture rightClickEntity(EntityLiving entity) {
        return interact(new InteractWithEntity(new WeakReference<>(entity), false));
    }

    public PathingRequestFuture leftClickEntity(EntityLiving entity) {
        return interact(new InteractWithEntity(new WeakReference<>(entity), true));
    }

    public PathingRequestFuture interact(InteractTarget target) {
        onLostControl();
        this.target = target;
        this.future = new PathingRequestFuture();
        return future;
    }

    @Override
    public boolean isActive() {
        return target != null;
    }

    @Override
    public PathingCommand onTick(final boolean calcFailed, final boolean isSafeToCancel, final Goal currentGoal, final PathingCommand prevCommand) {
        var t = target;
        if (t == null) {
            onLostControl();
            return null;
        }
        PathingCommand pathingCommand = t.pathingCommand();
        if (pathingCommand == null) {
            if (t.succeeded() && future != null) {
                future.complete(true);
                future.notifyListeners();
            }
            onLostControl();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        return pathingCommand;
    }

    @Override
    public void onLostControl() {
        target = null;
        if (future != null && !future.isCompleted()) {
            future.complete(false);
        }
        future = null;
    }

    @Override
    public String displayName0() {
        return "InteractWith: " + target;
    }

    public interface InteractTarget {
        PathingCommand pathingCommand();
        boolean succeeded();
    }

    @Data
    public static class InteractWithBlock implements InteractTarget {
        private final int x;
        private final int y;
        private final int z;
        private final boolean leftClick;
        private boolean succeeded = false;

        @Override
        public PathingCommand pathingCommand() {
            if (!targetValid() || succeeded) return null;
            if (canInteract()) {
                interact();
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            // todo: some antistuck func here
            return new PathingCommand(new GoalNear(x, y, z, 1), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
        }

        @Override
        public boolean succeeded() {
            return this.succeeded;
        }

        public boolean targetValid() {
            if (World.isChunkLoadedBlockPos(x, z)) {
                Block block = World.getBlock(x, y, z);
                if (BLOCK_DATA.isAir(block)) return false;
                var cbs = BLOCK_DATA.getInteractionBoxesFromBlockStateId(World.getBlockStateId(x, y, z));
                if (cbs.isEmpty()) return false;
            }
            return true;
        }

        public boolean canInteract() {
            Position center = World.blockInteractionCenter(x, y, z);
            Vector2f rotation = RotationHelper.rotationTo(center.x(), center.y(), center.z());
            var blockRaycastResult = RaycastHelper.playerEyeRaycastThroughToBlockTarget(x, y, z, rotation.getX(), rotation.getY());
            if (!blockRaycastResult.hit()) return false;
            if (blockRaycastResult.x() != x || blockRaycastResult.y() != y || blockRaycastResult.z() != z) return false;
            return true;
        }

        public void interact() {
            var in = Input.builder()
                .hand(Hand.MAIN_HAND)
                .clickTarget(new ClickTarget.BlockPosition(x, y, z));
            if (leftClick) {
                in.leftClick(true);
            } else {
                in.rightClick(true);
            }
            Position center = World.blockInteractionCenter(x, y, z);
            Vector2f rot = RotationHelper.rotationTo(center.x(), center.y(), center.z());
            INPUTS.submit(
                InputRequest.builder()
                    .owner(this)
                    .input(in.build())
                    .yaw(rot.getX())
                    .pitch(rot.getY())
                    .priority(Baritone.MOVEMENT_PRIORITY + 1)
                    .build())
                .addInputExecutedListener(future -> {
                    if (futureSucceeded(future)) {
                        PATH_LOG.info("{} clicked block at: [{}, {}, {}]", leftClick ? "left" : "right", x, y, z);
                        succeeded = true;
                    }
                });
        }

        public boolean futureSucceeded(InputRequestFuture future) {
            if (!future.getNow()) return false;
            if (leftClick) {
                if (!(future.getClickResult() instanceof ClickResult.LeftClickResult leftClickResult)) return false;
                return leftClickResult.getBlockX() == x && leftClickResult.getBlockY() == y && leftClickResult.getBlockZ() == z;
            } else {
                if (!(future.getClickResult() instanceof ClickResult.RightClickResult rightClickResult)) return false;
                return rightClickResult.getBlockX() == x && rightClickResult.getBlockY() == y && rightClickResult.getBlockZ() == z;
            }
        }
    }

    @Data
    public static class InteractWithEntity implements InteractTarget {
        private final WeakReference<EntityLiving> entityRef;
        private final boolean leftClick;
        private boolean succeeded = false;

        @Override
        public PathingCommand pathingCommand() {
            var entity = entityRef.get();
            if (entity == null) return null;
            if (!targetValid() || succeeded) return null;
            if (canInteract()) {
                interact();
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            // todo: some antistuck func here
            return new PathingCommand(new GoalNear(entity.blockPos(), 1), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
        }

        @Override
        public boolean succeeded() {
            return this.succeeded;
        }

        public boolean targetValid() {
            var entity = entityRef.get();
            if (entity == null) return false;
            if (CACHE.getEntityCache().get(entity.getEntityId()) != entity) return false;
            LocalizedCollisionBox cb = ENTITY_DATA.getCollisionBox(entity);
            if (cb == null) return false;
            return true;
        }

        public boolean canInteract() {
            var entity = entityRef.get();
            if (entity == null) return false;
            Vector2f rotation = RotationHelper.shortestRotationTo(entity);
            var raycastResult = RaycastHelper.playerEyeRaycastThroughToTarget(entity, rotation.getX(), rotation.getY());
            if (!raycastResult.hit()) return false;
            if (raycastResult.entity() != entity) return false;
            return true;
        }

        public void interact() {
            var entity = entityRef.get();
            if (entity == null) return;
            var in = Input.builder()
                .hand(Hand.MAIN_HAND)
                .clickTarget(new ClickTarget.EntityInstance(entity));
            if (leftClick) {
                in.leftClick(true);
            } else {
                in.rightClick(true);
            }
            Vector2f rot = RotationHelper.shortestRotationTo(entity);
            INPUTS.submit(
                InputRequest.builder()
                    .owner(this)
                    .input(in.build())
                    .yaw(rot.getX())
                    .pitch(rot.getY())
                    .priority(Baritone.MOVEMENT_PRIORITY + 1)
                    .build())
                .addInputExecutedListener(future -> {
                    if (futureSucceeded(future)) {
                        var pos = entity.blockPos();
                        PATH_LOG.info("{} clicked entity: {} at: [{}, {}, {}]", leftClick ? "left" : "right", entity.getEntityType(), pos.x(), pos.y(), pos.z());
                        succeeded = true;
                    }
                });
        }

        public boolean futureSucceeded(InputRequestFuture future) {
            if (!future.getNow()) return false;
            if (leftClick) {
                if (!(future.getClickResult() instanceof ClickResult.LeftClickResult leftClickResult)) return false;
                return leftClickResult.getEntity() == entityRef.get();
            } else {
                if (!(future.getClickResult() instanceof ClickResult.RightClickResult rightClickResult)) return false;
                return rightClickResult.getEntity() == entityRef.get();
            }
        }
    }
}
