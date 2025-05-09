package com.zenith.feature.inventory.actions;

import lombok.Data;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundPlaceRecipePacket;
import org.jspecify.annotations.Nullable;

@Data
public class PlaceRecipe implements InventoryAction {
    private final int containerId;
    /**
     * Warning: unstable interface
     * MC 1.21.4 uses integer recipe id's instead of identifiers
     */
    private final String recipeId;
    private final boolean useMaxItems;

    @Override
    public int containerId() {
        return containerId;
    }

    @Override
    public @Nullable MinecraftPacket packet() {
        return new ServerboundPlaceRecipePacket(containerId, recipeId, useMaxItems);
    }
}
