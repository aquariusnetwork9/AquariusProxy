package com.zenith.feature.pathfinder.util;

import com.zenith.cache.data.inventory.Container;
import com.zenith.mc.block.Block;
import com.zenith.mc.enchantment.EnchantmentRegistry;
import com.zenith.mc.item.ItemData;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.mc.item.ToolTag;
import com.zenith.module.impl.PlayerSimulation;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemEnchantments;

import java.util.List;

import static com.zenith.Shared.CACHE;
import static com.zenith.Shared.MODULE;

public class ToolSet {
    private final Int2DoubleMap blockBreakSpeedCache = new Int2DoubleOpenHashMap();

    public double getStrVsBlock(final Block block) {
        if (blockBreakSpeedCache.containsKey(block.id())) {
            return blockBreakSpeedCache.get(block.id());
        }
        int bestSlot = getBestSlot(block, false, true);
        ItemStack itemStack = CACHE.getPlayerCache().getPlayerInventory().get(36 + bestSlot);
        double blockBreakSpeed = MODULE.get(PlayerSimulation.class).getInteractions().blockBreakSpeed(block, itemStack);
        if (blockBreakSpeed <= 0) {
            blockBreakSpeedCache.put(block.id(), -1);
            return -1;
        }
        if (blockBreakSpeed >= 1) {
            blockBreakSpeedCache.put(block.id(), 1);
            return 1;
        }
        blockBreakSpeedCache.put(block.id(), blockBreakSpeed);
        return blockBreakSpeed;
    }

    // best slot in hotbar
    public int getBestSlot(Block b, boolean preferSilkTouch) {
        return getBestSlot(b, preferSilkTouch, false);
    }

    // best slot in hotbar
    public int getBestSlot(Block b, boolean preferSilkTouch, boolean pathingCalculation) {

        /*
        If we actually want know what efficiency our held item has instead of the best one
        possible, this lets us make pathing depend on the actual tool to be used (if auto tool is disabled)
        */
//        if (!Baritone.settings().autoTool.value && pathingCalculation) {
//            return player.getInventory().selected;
//        }

        int best = 0;
        double highestSpeed = Double.NEGATIVE_INFINITY;
        int lowestCost = Integer.MIN_VALUE;
        boolean bestSilkTouch = false;
        List<ItemStack> playerInventory = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 36; i <= 44; i++) {
            ItemStack itemStack = playerInventory.get(i);
//            if (!Baritone.settings().useSwordToMine.value && itemStack.getItem() instanceof SwordItem) {
//                continue;
//            }

//            if (Baritone.settings().itemSaver.value && (itemStack.getDamageValue() + Baritone.settings().itemSaverThreshold.value) >= itemStack.getMaxDamage() && itemStack.getMaxDamage() > 1) {
//                continue;
//            }
            double speed = MODULE.get(PlayerSimulation.class).getInteractions().blockBreakSpeed(b, itemStack);
            boolean silkTouch = hasSilkTouch(itemStack);
            if (speed > highestSpeed) {
                highestSpeed = speed;
                best = i;
                lowestCost = getMaterialCost(itemStack);
                bestSilkTouch = silkTouch;
            } else if (speed == highestSpeed) {
                int cost = getMaterialCost(itemStack);
                if ((cost < lowestCost && (silkTouch || !bestSilkTouch)) ||
                    (preferSilkTouch && !bestSilkTouch && silkTouch)) {
                    highestSpeed = speed;
                    best = i;
                    lowestCost = cost;
                    bestSilkTouch = silkTouch;
                }
            }
        }
        return best - 36;
    }

    // wood = least expensive
    // netherite = most expensive
    private int getMaterialCost(final ItemStack itemStack) {
        if (itemStack == Container.EMPTY_STACK) return -1;
        ItemData itemData = ItemRegistry.REGISTRY.get(itemStack.getId());
        if (itemData == null) return -1;
        ToolTag toolTag = itemData.toolTag();
        if (toolTag == null) return -1;
        return toolTag.tier().ordinal();
    }

    private boolean hasSilkTouch(final ItemStack itemStack) {
        if (itemStack == Container.EMPTY_STACK) return false;
        DataComponents dataComponents = itemStack.getDataComponents();
        if (dataComponents == null) return false;
        ItemEnchantments itemEnchantments = dataComponents.get(DataComponentTypes.ENCHANTMENTS);
        if (itemEnchantments == null) return false;
        return itemEnchantments.getEnchantments().containsKey(EnchantmentRegistry.SILK_TOUCH.id());
    }
}
