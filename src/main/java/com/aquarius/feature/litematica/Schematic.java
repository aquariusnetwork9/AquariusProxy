package com.aquarius.feature.litematica;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed schematic, normalised across the supported formats ({@code .litematic} and vanilla structure
 * {@code .nbt}). Block coordinates are flattened to a single, format-independent list relative to the schematic's
 * minimum corner ({@code (0,0,0)} = the lowest north-west-down cell of the enclosing box), so the build engine and
 * the unit tests never touch format-specific packing. Only placeable, non-air cells are kept; air is dropped and
 * unplaceable non-air cells (fluids, state-only blocks, …) are tallied into {@link #skipped()}.
 *
 * @param name        display name (schematic metadata name, else the file name)
 * @param sizeX       enclosing box width  (X)
 * @param sizeY       enclosing box height (Y)
 * @param sizeZ       enclosing box length (Z)
 * @param dataVersion Minecraft data version the schematic was saved with (0 if unknown)
 * @param blocks      placeable cells, relative to the schematic's minimum corner
 * @param skipped     count of non-air cells that have no placeable item in v1 (logged, not built)
 * @param materials   item -> count tally over {@link #blocks}, highest count first
 */
public record Schematic(
    String name,
    int sizeX,
    int sizeY,
    int sizeZ,
    int dataVersion,
    List<BlockEntry> blocks,
    int skipped,
    List<MaterialEntry> materials
) {
    /** One placeable cell, relative to the schematic minimum corner. */
    public record BlockEntry(int x, int y, int z, PaletteEntry entry) {}

    /** A material requirement: an item name and how many of it the schematic needs. */
    public record MaterialEntry(String item, int count) {}

    public int totalBlocks() {
        return blocks.size();
    }

    /** Tally a block list into a material requirement list (item -> count), highest count first. */
    public static List<MaterialEntry> tally(List<BlockEntry> blocks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BlockEntry b : blocks) {
            var item = b.entry().placeItem();
            if (item == null) continue;
            counts.merge(item.name(), 1, Integer::sum);
        }
        List<MaterialEntry> out = new ArrayList<>(counts.size());
        for (var e : counts.entrySet()) out.add(new MaterialEntry(e.getKey(), e.getValue()));
        out.sort(Comparator.comparingInt(MaterialEntry::count).reversed());
        return out;
    }
}
