package com.zenith.feature.world;

import com.zenith.cache.data.entity.Entity;
import com.zenith.feature.world.raycast.BlockOrEntityRaycastResult;
import com.zenith.feature.world.raycast.RaycastHelper;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;

public interface ClickTarget {
    default BlockOrEntityRaycastResult apply(double blockReachDistance, double entityInteractDistance) {
        var ray = raycast(blockReachDistance, entityInteractDistance);
        return validateRaycast(ray) ? ray : BlockOrEntityRaycastResult.miss();
    }

    default boolean validateRaycast(BlockOrEntityRaycastResult raycastResult) {
        return true;
    }

    default BlockOrEntityRaycastResult raycast(double blockReachDistance, double entityInteractDistance) {
        return RaycastHelper.playerBlockOrEntityRaycast(blockReachDistance, entityInteractDistance);
    }

    class None implements ClickTarget {
        public static final None INSTANCE = new None();

        @Override
        public boolean validateRaycast(final BlockOrEntityRaycastResult raycastResult) {
            return false;
        }

        @Override
        public BlockOrEntityRaycastResult raycast(final double blockReachDistance, final double entityInteractDistance) {
            return BlockOrEntityRaycastResult.miss();
        }
    }

    class AnyBlock implements ClickTarget {
        public static final AnyBlock INSTANCE = new AnyBlock();

        @Override
        public BlockOrEntityRaycastResult raycast(final double blockReachDistance, final double entityInteractDistance) {
            return BlockOrEntityRaycastResult.wrap(RaycastHelper.playerBlockRaycast(blockReachDistance, false));
        }
    }

    class Any implements ClickTarget {
        public static final Any INSTANCE = new Any();
    }

    @AllArgsConstructor
    class BlockPosition extends AnyBlock {
        private final int x;
        private final int y;
        private final int z;

        @Override
        public boolean validateRaycast(final BlockOrEntityRaycastResult raycastResult) {
            return raycastResult.isBlock()
                && raycastResult.block().hit()
                && raycastResult.block().x() == x
                && raycastResult.block().y() == y
                && raycastResult.block().z() == z;
        }
    }

    class AnyEntity implements ClickTarget {
        public static final AnyEntity INSTANCE = new AnyEntity();

        @Override
        public BlockOrEntityRaycastResult raycast(final double blockReachDistance, final double entityInteractDistance) {
            return BlockOrEntityRaycastResult.wrap(RaycastHelper.playerEntityRaycast(entityInteractDistance));
        }
    }

    @RequiredArgsConstructor
    class EntityId extends AnyEntity {
        private final int entityId;

        @Override
        public boolean validateRaycast(final BlockOrEntityRaycastResult raycastResult) {
            return raycastResult.isEntity()
                && raycastResult.entity().hit()
                && raycastResult.entity().entity().getEntityId() == entityId;
        }
    }

    @RequiredArgsConstructor
    class EntityTypeTarget extends AnyEntity {
        private final EntityType entityType;

        @Override
        public boolean validateRaycast(final BlockOrEntityRaycastResult raycastResult) {
            return raycastResult.isEntity()
                && raycastResult.entity().hit()
                && raycastResult.entity().entity().getEntityType() == entityType;
        }
    }

    @RequiredArgsConstructor
    class EntityInstance extends AnyEntity {
        private final Entity entity;

        @Override
        public boolean validateRaycast(final BlockOrEntityRaycastResult raycastResult) {
            return raycastResult.isEntity()
                && raycastResult.entity().hit()
                && raycastResult.entity().entity().equals(entity);
        }
    }
}
