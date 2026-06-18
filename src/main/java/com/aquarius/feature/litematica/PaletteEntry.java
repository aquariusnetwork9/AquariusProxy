package com.aquarius.feature.litematica;

import com.aquarius.mc.item.ItemData;
import org.jspecify.annotations.Nullable;

/**
 * One resolved entry of a schematic's block-state palette: the (namespace-stripped) block name plus the
 * {@link ItemData} needed to place it. Block-state properties (facing/half/axis/…) are intentionally not retained
 * — v1 places the base block only (see {@link BlockItemMapping}). An entry is {@link #placeable()} when it is not
 * air and a placement item exists.
 */
public final class PaletteEntry {
    private final String blockName;
    private final boolean air;
    private final @Nullable ItemData placeItem;

    public PaletteEntry(String rawName) {
        this.blockName = BlockItemMapping.strip(rawName);
        this.air = blockName.equals("air") || blockName.equals("cave_air") || blockName.equals("void_air");
        this.placeItem = BlockItemMapping.itemFor(rawName);
    }

    /** Namespace-stripped block name, matching {@code World.getBlock(...).name()}. */
    public String blockName() {
        return blockName;
    }

    public boolean isAir() {
        return air;
    }

    /** The item to hold to place this block, or {@code null} if it is not placeable in v1. */
    public @Nullable ItemData placeItem() {
        return placeItem;
    }

    /** True when this block should be built: not air and a placement item exists. */
    public boolean placeable() {
        return !air && placeItem != null;
    }
}
