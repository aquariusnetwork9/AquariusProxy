package com.zenith.feature.inventory.actions;

import com.zenith.cache.data.inventory.Container;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Interface for an inventory action submitted through the InventoryManager
 *
 * Each action is associated with an expected container id.
 * If the container is not open at the time which the action is to be executed, it is skipped.
 *
 * InventoryActions return a packet that is lazily constructed at action execution time.
 * Meaning the action will reference up-to-date inventory cache data.
 * Actions can return a null packet to indicate that the action should be skipped.
 */
public interface InventoryAction {
    int containerId();

    @Nullable MinecraftPacket packet();

    default boolean isStackEmpty(ItemStack stack) {
        return stack == Container.EMPTY_STACK;
    }
}
