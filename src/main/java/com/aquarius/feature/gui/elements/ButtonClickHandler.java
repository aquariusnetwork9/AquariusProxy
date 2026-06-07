package com.aquarius.feature.gui.elements;

import com.aquarius.feature.gui.ContainerClick;
import com.aquarius.feature.gui.Gui;
import com.aquarius.feature.gui.Page;

@FunctionalInterface
public interface ButtonClickHandler {
    void accept(Button button, Gui gui, Page page, ContainerClick containerClick);
}
