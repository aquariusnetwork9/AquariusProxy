package com.aquarius.feature.pathfinder.util;

import com.aquarius.cache.data.inventory.Container;
import com.aquarius.mc.block.Block;
import com.aquarius.mc.enchantment.EnchantmentRegistry;
import com.aquarius.mc.item.ItemData;
import com.aquarius.mc.item.ItemRegistry;
import com.aquarius.mc.item.ToolTag;
import com.aquarius.util.ItemUtil;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemEnchantments;

import java.util.List;

import static com.aquarius.Globals.*;

public class ToolSet {
    private final Int2DoubleMap blockBreakSpeedCache = new Int2DoubleOpenHashMap();

    public double getStrVsBlock(final Block block) {
        if (blockBreakSpeedCache.containsKey(block.id())) {
            return blockBreakSpeedCache.get(block.id());
        }
        int bestSlot = getBestSlot(block, false, true);
        ItemStack itemStack = CACHE.getPlayerCache().getPlayerInventory().get(36 + bestSlot);
        double blockBreakSpeed = BOT.getInteractions().blockBreakSpeed(block, itemStack);
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
        if (!CONFIG.client.extra.pathfinder.autoTool && pathingCalculation) {
            return CACHE.getPlayerCache().getHeldItemSlot();
        }

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

            if (CONFIG.client.extra.pathfinder.itemSaver && ItemUtil.getMaxDamage(itemStack) > 1 && ItemUtil.getDamageUntilBreak(itemStack) <= CONFIG.client.extra.pathfinder.itemSaverThreshold) {
                continue;
            }
            double speed = BOT.getInteractions().blockBreakSpeed(b, itemStack);
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

    /** Best hotbar slot (0-8) for breaking {@code b} using only NON-silk tools (fortune/plain), by break speed.
     *  -1 if the hotbar holds no non-silk tool. Mirrors the miner's silk reservation for fortune ore runs. */
    public int getBestNonSilkSlot(Block b) {
        return bestSlotMatchingSilk(b, false);
    }

    /** Best hotbar slot (0-8) for breaking {@code b} using only SILK-TOUCH tools, by break speed.
     *  -1 if the hotbar holds no silk-touch tool. */
    public int getBestSilkSlot(Block b) {
        return bestSlotMatchingSilk(b, true);
    }

    private int bestSlotMatchingSilk(Block b, boolean wantSilk) {
        int best = -1;
        double highestSpeed = Double.NEGATIVE_INFINITY;
        int lowestCost = Integer.MIN_VALUE;
        List<ItemStack> playerInventory = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 36; i <= 44; i++) {
            ItemStack itemStack = playerInventory.get(i);
            if (itemStack == Container.EMPTY_STACK) continue;
            if (hasSilkTouch(itemStack) != wantSilk) continue;
            if (CONFIG.client.extra.pathfinder.itemSaver && ItemUtil.getMaxDamage(itemStack) > 1
                && ItemUtil.getDamageUntilBreak(itemStack) <= CONFIG.client.extra.pathfinder.itemSaverThreshold) {
                continue;
            }
            double speed = BOT.getInteractions().blockBreakSpeed(b, itemStack);
            int cost = getMaterialCost(itemStack);
            if (speed > highestSpeed || (speed == highestSpeed && cost < lowestCost)) {
                highestSpeed = speed;
                lowestCost = cost;
                best = i - 36;
            }
        }
        return best;
    }

    // wood = least expensive
    // netherite = most expensive
    public int getMaterialCost(final ItemStack itemStack) {
        if (itemStack == Container.EMPTY_STACK) return -1;
        ItemData itemData = ItemRegistry.REGISTRY.get(itemStack.getId());
        if (itemData == null) return -1;
        ToolTag toolTag = itemData.toolTag();
        if (toolTag == null) return -1;
        return toolTag.tier().ordinal();
    }

    public boolean hasSilkTouch(final ItemStack itemStack) {
        if (itemStack == Container.EMPTY_STACK) return false;
        DataComponents dataComponents = itemStack.getDataComponentsOrEmpty();
        ItemEnchantments itemEnchantments = dataComponents.get(DataComponentTypes.ENCHANTMENTS);
        if (itemEnchantments == null) return false;
        return itemEnchantments.getEnchantments().containsKey(EnchantmentRegistry.SILK_TOUCH.get().id());
    }
}
