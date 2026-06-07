package com.aquarius.feature.gui.elements;

import com.aquarius.feature.gui.Gui;
import com.aquarius.feature.gui.Page;

@FunctionalInterface
public interface ItemSlotTickHandler {
    void tick(ItemSlot slot, Gui gui, Page page, int index);
}
