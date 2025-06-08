package com.zenith.feature.inventory.actions;

import com.zenith.mc.item.ItemData;
import com.zenith.mc.item.ItemRegistry;
import lombok.Data;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.ItemStackSlotDisplay;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundPlaceRecipePacket;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CLIENT_LOG;

/**
 * Warning: unstable interface
 * May change in future MC versions
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
        // chop off the namespace prefix if it exists (like "minecraft:emerald_block" -> "emerald_block")
        String recipeKey;
        try {
            recipeKey = Key.key(recipeId).value();
        } catch (Exception e) {
            CLIENT_LOG.debug("Invalid recipe key: {}", recipeId, e);
            return null;
        }
        ItemData itemData = ItemRegistry.REGISTRY.get(recipeKey);
        if (itemData == null) {
            CLIENT_LOG.debug("No item data found for recipe {}", this);
            return null;
        }
        for (var recipeBookEntry : CACHE.getRecipeCache().getRecipeBookEntries().int2ObjectEntrySet()) {
            var displayResult = recipeBookEntry.getValue().display().result();
            if (displayResult instanceof ItemStackSlotDisplay(ItemStack itemStack)) {
                if (itemStack != null && itemStack.getId() == itemData.id()) {
                    return new ServerboundPlaceRecipePacket(containerId, recipeBookEntry.getIntKey(), useMaxItems);
                }
            }
        }
        CLIENT_LOG.debug("No matching recipe found {}", this);
        return null;
    }
}
