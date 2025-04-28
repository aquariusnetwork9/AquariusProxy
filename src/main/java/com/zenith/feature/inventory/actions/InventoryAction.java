package com.zenith.feature.inventory.actions;

import com.zenith.cache.data.inventory.Container;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jspecify.annotations.Nullable;

public interface InventoryAction {
    int containerId();

    @Nullable MinecraftPacket packet();

    default boolean isStackEmpty(ItemStack stack) {
        return stack == Container.EMPTY_STACK;
    }

    default String type() {
        return this.getClass().getSimpleName();
    }
}
