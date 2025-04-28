package com.zenith.feature.inventory.actions;

import com.google.common.collect.Lists;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;

import java.util.List;

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
}
