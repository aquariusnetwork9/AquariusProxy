package com.zenith.feature.inventory.util;

import com.google.common.collect.Lists;
import com.zenith.cache.data.inventory.Container;
import com.zenith.feature.inventory.actions.ClickItem;
import com.zenith.feature.inventory.actions.InventoryAction;
import com.zenith.feature.inventory.actions.ShiftClick;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static com.zenith.Globals.CACHE;

// utils for using a series of inventory actions
public class InventoryActionMacros {
    private InventoryActionMacros() {}

    // player inventory only
    public static List<InventoryAction> swapSlots(int fromSlot, int toSlot) {
        return Lists.newArrayList(
            new ClickItem(fromSlot, ClickItemAction.LEFT_CLICK),
            new ClickItem(toSlot, ClickItemAction.LEFT_CLICK),
            new ClickItem(fromSlot, ClickItemAction.LEFT_CLICK)
        );
    }

    public static List<InventoryAction> swapSlots(int containerId, int fromSlot, int toSlot) {
        return Lists.newArrayList(
            new ClickItem(containerId, fromSlot, ClickItemAction.LEFT_CLICK),
            new ClickItem(containerId, toSlot, ClickItemAction.LEFT_CLICK),
            new ClickItem(containerId, fromSlot, ClickItemAction.LEFT_CLICK)
        );
    }

    public static List<InventoryAction> withdraw(int containerId) {
        return withdraw(containerId, i -> true);
    }

    public static List<InventoryAction> withdraw(int containerId, Predicate<ItemStack> predicate) {
        var container = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        int openContainerId = container.getContainerId();
        if (openContainerId != containerId) {
            return Collections.emptyList();
        }
        List<InventoryAction> actions = new ArrayList<>();
        final int containerTopInvEndIndex = container.getSize() - 36;
        for (int i = 0; i < containerTopInvEndIndex; i++) {
            if (container.getItemStack(i) == Container.EMPTY_STACK) continue;
            if (!predicate.test(container.getItemStack(i))) continue;
            actions.add(new ShiftClick(containerId, i, ShiftClickItemAction.LEFT_CLICK));
        }
        return actions;
    }

    public static List<InventoryAction> deposit(int containerId) {
        return deposit(containerId, i -> true);
    }

    public static List<InventoryAction> deposit(int containerId, Predicate<ItemStack> predicate) {
        var container = CACHE.getPlayerCache().getInventoryCache().getOpenContainer();
        int openContainerId = container.getContainerId();
        if (openContainerId != containerId) {
            return Collections.emptyList();
        }
        List<InventoryAction> actions = new ArrayList<>();
        final int containerTopInvEndIndex = container.getSize() - 36;
        for (int i = container.getSize() - 1; i >= containerTopInvEndIndex; i--) {
            if (container.getItemStack(i) == Container.EMPTY_STACK) continue;
            if (!predicate.test(container.getItemStack(i))) continue;
            actions.add(new ShiftClick(containerId, i, ShiftClickItemAction.LEFT_CLICK));
        }
        return actions;
    }
}
