package com.zenith.feature.inventory.actions;

import lombok.Data;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.jspecify.annotations.Nullable;

import static com.zenith.Globals.CACHE;

/**
 * No-op. Lets you add a delay between actions.
 */
@Data
public class WaitAction implements InventoryAction {
    @Override
    public int containerId() {
        return CACHE.getPlayerCache().getInventoryCache().getOpenContainerId();
    }

    @Override
    public @Nullable MinecraftPacket packet() {
        return null;
    }
}
