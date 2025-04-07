package com.zenith.feature.player;

import com.zenith.cache.data.entity.Entity;
import com.zenith.mc.block.Block;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public interface ClickResult {

    class None implements ClickResult {
        private None() {}
        public static final None INSTANCE = new None();
    }

    /**
     * Left click
     *  -> block start destroy (return block position and instance)
     *  -> block continue destroy (return block position and instance)
     *  -> entity attack (return entity instance)
     *  -> swing
     *
     * Right click
     *  -> use item on block (return block position and instance)
     *  -> use item on entity (return entity instance)
     *  -> use item
     */

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    class LeftClickResult implements ClickResult {
        private final int blockX;
        private final int blockY;
        private final int blockZ;
        private final Block block;
        private final Entity entity;
        private final LeftClickType type;

        public enum LeftClickType {
            START_DESTROY_BLOCK,
            CONTINUE_DESTROY_BLOCK,
            ATTACK_ENTITY,
            SWING
        }

        public static LeftClickResult startDestroyBlock(int blockX, int blockY, int blockZ, Block block) {
            return new LeftClickResult(blockX, blockY, blockZ, block, null, LeftClickType.START_DESTROY_BLOCK);
        }

        public static LeftClickResult continueDestroyBlock(int blockX, int blockY, int blockZ, Block block) {
            return new LeftClickResult(blockX, blockY, blockZ, block, null, LeftClickType.CONTINUE_DESTROY_BLOCK);
        }

        public static LeftClickResult attackEntity(Entity entity) {
            return new LeftClickResult(0, 0, 0, null, entity, LeftClickType.ATTACK_ENTITY);
        }

        public static LeftClickResult swing() {
            return new LeftClickResult(0, 0, 0, null, null, LeftClickType.SWING);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    class RightClickResult implements ClickResult {
        private final int blockX;
        private final int blockY;
        private final int blockZ;
        private final Block block;
        private final Entity entity;
        private final RightClickType type;

        public enum RightClickType {
            USE_ITEM_ON_BLOCK,
            USE_ITEM_ON_ENTITY,
            USE_ITEM
        }

        public static RightClickResult useItemOnBlock(int blockX, int blockY, int blockZ, Block block) {
            return new RightClickResult(blockX, blockY, blockZ, block, null, RightClickType.USE_ITEM_ON_BLOCK);
        }

        public static RightClickResult useItemOnEntity(Entity entity) {
            return new RightClickResult(0, 0, 0, null, entity, RightClickType.USE_ITEM_ON_ENTITY);
        }

        public static RightClickResult useItem() {
            return new RightClickResult(0, 0, 0, null, null, RightClickType.USE_ITEM);
        }
    }
}
