package com.zenith.feature.inventory.actions;

import lombok.Data;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundPlaceRecipePacket;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Warning: unstable interface
 * MC 1.21.4+ uses integer recipe id's instead of identifiers
 *
 * To find recipes, query `Globals.CACHE.getRecipeCache().getRecipeRegistry()`
 */
@Data
@ApiStatus.Experimental
public class PlaceRecipe implements InventoryAction {
    private final int containerId;
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
