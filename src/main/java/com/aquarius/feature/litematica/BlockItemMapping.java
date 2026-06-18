package com.aquarius.feature.litematica;

import com.aquarius.mc.item.ItemData;
import com.aquarius.mc.item.ItemRegistry;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Maps a schematic block name to the {@link ItemData} the builder must hold to place it. For the full solid
 * blocks v1 supports, the item name equals the block name (e.g. {@code stone}, {@code cobblestone},
 * {@code oak_planks}); a small set of blocks that have no placeable item (air variants, fluids, fire, technical
 * blocks) is rejected so the build engine skips them instead of getting stuck. Block-state / orientation fidelity
 * (stairs, slabs, logs, waterlogged, …) is out of scope for v1 — {@code BARITONE.placeBlock} is item-only — so
 * those still place as their base block.
 */
public final class BlockItemMapping {
    private BlockItemMapping() {}

    /** Blocks with no direct placeable item (fluids/air/technical). Most also fail the registry lookup, but listing
     *  them keeps the intent explicit and guards against an item sharing the block's name. */
    private static final Set<String> UNPLACEABLE = Set.of(
        "air", "cave_air", "void_air",
        "water", "lava", "bubble_column",
        "fire", "soul_fire",
        "nether_portal", "end_portal", "end_gateway",
        "moving_piston", "piston_head",
        "frosted_ice", "tripwire");

    /** Strip a {@code minecraft:} (or any) namespace prefix from a resource id. */
    public static String strip(String name) {
        int i = name.indexOf(':');
        return i < 0 ? name : name.substring(i + 1);
    }

    /** The item to place for {@code blockName}, or {@code null} if the block is not placeable in v1. */
    public static @Nullable ItemData itemFor(String blockName) {
        String n = strip(blockName);
        if (UNPLACEABLE.contains(n)) return null;
        return ItemRegistry.REGISTRY.get(n);
    }
}
