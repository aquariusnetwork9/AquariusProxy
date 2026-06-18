package com.aquarius.feature.litematica;

import com.aquarius.feature.player.World;
import com.aquarius.mc.item.ItemData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Turns a {@link Schematic} plus a world origin into an ordered placement queue and tracks build progress against
 * the live world. Placements are ordered bottom-up (Y ascending) then raster within a layer (Z, then X) so the bot
 * builds outward from one corner and never seals itself in. The plan is <i>resume-safe</i>: a cell that already
 * matches the target block (by name) is treated as done, so a partially-built or re-loaded structure is not
 * rebuilt. A cell the builder cannot place (floating with no support, repeatedly lag-rejected) can be
 * {@link #skip(Placement) skipped} so the run completes instead of deadlocking.
 *
 * <p>Block-state fidelity is out of scope for v1, so "satisfied" compares the placed block's name only.
 */
public final class BuildPlan {

    /** One target placement in absolute world coordinates. */
    public record Placement(int x, int y, int z, PaletteEntry entry) {}

    private final List<Placement> placements;
    private final boolean[] skipped;
    private int cursor = 0;

    public BuildPlan(Schematic schematic, int originX, int originY, int originZ) {
        List<Placement> list = new ArrayList<>(schematic.blocks().size());
        for (Schematic.BlockEntry b : schematic.blocks()) {
            list.add(new Placement(originX + b.x(), originY + b.y(), originZ + b.z(), b.entry()));
        }
        list.sort(Comparator.comparingInt(Placement::y)
            .thenComparingInt(Placement::z)
            .thenComparingInt(Placement::x));
        this.placements = list;
        this.skipped = new boolean[list.size()];
    }

    private boolean satisfied(Placement p) {
        return World.getBlock(p.x(), p.y(), p.z()).name().equals(p.entry().blockName());
    }

    /** Advance the cursor past any leading placements that are already done (placed or skipped). */
    public void advanceSatisfied() {
        while (cursor < placements.size() && (skipped[cursor] || satisfied(placements.get(cursor)))) cursor++;
    }

    public boolean isComplete() {
        advanceSatisfied();
        return cursor >= placements.size();
    }

    /** The next unsatisfied placement whose item the caller holds, or {@code null} if none is currently placeable. */
    public @Nullable Placement next(Predicate<ItemData> haveItem) {
        advanceSatisfied();
        for (int i = cursor; i < placements.size(); i++) {
            if (skipped[i]) continue;
            Placement p = placements.get(i);
            if (satisfied(p)) continue;
            ItemData it = p.entry().placeItem();
            if (it != null && haveItem.test(it)) return p;
        }
        return null;
    }

    /** Permanently skip a placement (e.g. unplaceable after retries) so the run can still complete. */
    public void skip(Placement p) {
        int i = placements.indexOf(p);
        if (i >= 0) skipped[i] = true;
    }

    /** The next up-to-{@code max} unsatisfied placements (for waypoint display). Does not advance the cursor, so it
     *  is safe to call from a different thread than the build tick. */
    public List<Placement> upcoming(int max) {
        List<Placement> out = new ArrayList<>(Math.min(max, 16));
        for (int i = cursor; i < placements.size() && out.size() < max; i++) {
            if (skipped[i]) continue;
            Placement p = placements.get(i);
            if (!satisfied(p)) out.add(p);
        }
        return out;
    }

    public int total() {
        return placements.size();
    }

    /** How many placements are done (placed or skipped). O(n) — for status/progress display. */
    public int doneCount() {
        int n = 0;
        for (int i = 0; i < placements.size(); i++) {
            if (skipped[i] || satisfied(placements.get(i))) n++;
        }
        return n;
    }

    /** Item -> count tally over the still-unsatisfied, non-skipped placements, highest count first. */
    public List<Schematic.MaterialEntry> remainingMaterials() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = cursor; i < placements.size(); i++) {
            if (skipped[i]) continue;
            Placement p = placements.get(i);
            if (satisfied(p)) continue;
            ItemData it = p.entry().placeItem();
            if (it != null) counts.merge(it.name(), 1, Integer::sum);
        }
        List<Schematic.MaterialEntry> out = new ArrayList<>(counts.size());
        for (var e : counts.entrySet()) out.add(new Schematic.MaterialEntry(e.getKey(), e.getValue()));
        out.sort(Comparator.comparingInt(Schematic.MaterialEntry::count).reversed());
        return out;
    }
}
