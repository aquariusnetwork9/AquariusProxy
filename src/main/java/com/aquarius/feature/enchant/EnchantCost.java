package com.aquarius.feature.enchant;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure Minecraft (Java Edition) anvil math for the {@link com.aquarius.module.impl.Enchanter}.
 *
 * <p>Deliberately has no game/registry dependency so the cost model can be reasoned about and
 * unit-tested in isolation. The enchant <b>item</b> multipliers (rarity weights 1/2/4/8) and max
 * levels are hardcoded — {@code mc/enchantment/EnchantmentData} only carries (id, name, maxLevel),
 * not the anvil cost multiplier, and these values are static, well-known constants.
 *
 * <p>Cost of one anvil combine (target = left slot, sacrifice = right slot), Java Edition rule:
 * <pre>cost = target.repairCost + sacrifice.repairCost
 *           + &Sigma; over each compatible enchant E on the SACRIFICE: finalLevel(E) &times; mult(E)</pre>
 * where {@code mult} is the enchant's item multiplier, halved (min 1) when the TARGET is a book.
 * The result inherits {@code max(target.repairCost, sacrifice.repairCost) * 2 + 1} as its new
 * prior-work penalty (the {@code minecraft:repair_cost} component stores this value directly:
 * 0, 1, 3, 7, 15, 31, ...). Any single combine whose cost is &ge; {@link #TOO_EXPENSIVE} is refused
 * by a survival anvil ("Too Expensive!").
 */
public final class EnchantCost {
    private EnchantCost() {}

    /** A single anvil operation costing this many levels or more is blocked in survival. */
    public static final int TOO_EXPENSIVE = 40;

    /** enchant registry name -> item anvil cost multiplier (book multiplier = max(1, item/2)). */
    private static final Map<String, Integer> ITEM_MULT = new HashMap<>();
    /** enchant registry name -> max level (used only for the rare equal-level upgrade bump). */
    private static final Map<String, Integer> MAX_LEVEL = new HashMap<>();

    private static void reg(String name, int itemMult, int maxLevel) {
        ITEM_MULT.put(name, itemMult);
        MAX_LEVEL.put(name, maxLevel);
    }

    static {
        // weight 1 (book mult 1)
        reg("protection", 1, 4);            reg("sharpness", 1, 5);             reg("efficiency", 1, 5);
        reg("power", 1, 5);                 reg("piercing", 1, 4);              reg("loyalty", 1, 3);
        // weight 2 (book mult 1)
        reg("fire_protection", 2, 4);       reg("feather_falling", 2, 4);       reg("projectile_protection", 2, 4);
        reg("smite", 2, 5);                 reg("bane_of_arthropods", 2, 5);    reg("knockback", 2, 2);
        reg("unbreaking", 2, 3);            reg("quick_charge", 2, 3);          reg("density", 2, 5);
        // weight 4 (book mult 2)
        reg("blast_protection", 4, 4);      reg("respiration", 4, 3);           reg("aqua_affinity", 4, 1);
        reg("depth_strider", 4, 3);         reg("frost_walker", 4, 2);          reg("fire_aspect", 4, 2);
        reg("looting", 4, 3);               reg("sweeping_edge", 4, 3);         reg("fortune", 4, 3);
        reg("punch", 4, 2);                 reg("flame", 4, 1);                 reg("luck_of_the_sea", 4, 3);
        reg("lure", 4, 3);                  reg("mending", 4, 1);               reg("impaling", 4, 5);
        reg("riptide", 4, 3);               reg("multishot", 4, 1);             reg("breach", 4, 4);
        reg("wind_burst", 4, 3);
        // weight 8 (book mult 4)
        reg("thorns", 8, 3);                reg("silk_touch", 8, 1);            reg("infinity", 8, 1);
        reg("channeling", 8, 1);            reg("binding_curse", 8, 1);         reg("vanishing_curse", 8, 1);
        reg("soul_speed", 8, 3);            reg("swift_sneak", 8, 3);
    }

    /** Mutually-exclusive enchant groups (only one of each group can sit on an item at once). */
    private static final List<Set<String>> INCOMPATIBLE = List.of(
        Set.of("sharpness", "smite", "bane_of_arthropods"),
        Set.of("protection", "blast_protection", "fire_protection", "projectile_protection"),
        Set.of("fortune", "silk_touch"),
        Set.of("infinity", "mending"),
        Set.of("multishot", "piercing"),
        Set.of("depth_strider", "frost_walker"),
        // riptide conflicts with loyalty AND channeling, but loyalty + channeling coexist — two pairs, not one group
        Set.of("riptide", "loyalty"),
        Set.of("riptide", "channeling"),
        Set.of("density", "breach")
    );

    public static int itemMult(String ench) { return ITEM_MULT.getOrDefault(ench, 1); }
    public static int bookMult(String ench) { return Math.max(1, itemMult(ench) / 2); }
    public static int maxLevel(String ench) { return MAX_LEVEL.getOrDefault(ench, 1); }
    public static boolean known(String ench) { return ITEM_MULT.containsKey(ench); }

    /** True if {@code ench} is in a mutually-exclusive group with any of {@code present} (excluding itself). */
    public static boolean conflicts(String ench, Set<String> present) {
        for (Set<String> group : INCOMPATIBLE) {
            if (!group.contains(ench)) continue;
            for (String p : present) if (!p.equals(ench) && group.contains(p)) return true;
        }
        return false;
    }

    /** New prior-work penalty after combining items with penalties a and b. */
    public static int resultRepairCost(int a, int b) { return Math.max(a, b) * 2 + 1; }

    public static boolean tooExpensive(int cost) { return cost >= TOO_EXPENSIVE; }

    /** An item or book as the optimizer / cost model sees it: its enchants, prior-work penalty, book-ness. */
    public static final class EnchItem {
        /** enchant registry name -> level. */
        public final Map<String, Integer> enchants;
        /** prior-work penalty value (0, 1, 3, 7, 15, 31, ...), i.e. the minecraft:repair_cost component. */
        public final int repairCost;
        public final boolean isBook;

        public EnchItem(Map<String, Integer> enchants, int repairCost, boolean isBook) {
            this.enchants = enchants;
            this.repairCost = repairCost;
            this.isBook = isBook;
        }

        /** A fresh single-enchant enchanted book (repair cost 0). */
        public static EnchItem book(String ench, int level) {
            Map<String, Integer> m = new LinkedHashMap<>();
            m.put(ench, level);
            return new EnchItem(m, 0, true);
        }

        /** A piece of gear with its current enchants + prior-work penalty. */
        public static EnchItem item(Map<String, Integer> enchants, int repairCost) {
            return new EnchItem(new LinkedHashMap<>(enchants), repairCost, false);
        }
    }

    /** Level cost of combining {@code target} (left slot) with {@code sacrifice} (right slot). */
    public static int combineCost(EnchItem target, EnchItem sacrifice) {
        int cost = target.repairCost + sacrifice.repairCost;
        for (Map.Entry<String, Integer> e : sacrifice.enchants.entrySet()) {
            String ench = e.getKey();
            if (conflicts(ench, target.enchants.keySet())) continue;      // not applied, no cost
            int finalLevel = finalLevel(ench, target.enchants.getOrDefault(ench, 0), e.getValue());
            cost += finalLevel * (target.isBook ? bookMult(ench) : itemMult(ench));
        }
        return cost;
    }

    /** Resulting item of combining {@code target} (left) with {@code sacrifice} (right). */
    public static EnchItem combine(EnchItem target, EnchItem sacrifice) {
        Map<String, Integer> out = new LinkedHashMap<>(target.enchants);
        for (Map.Entry<String, Integer> e : sacrifice.enchants.entrySet()) {
            String ench = e.getKey();
            if (conflicts(ench, out.keySet())) continue;
            out.put(ench, finalLevel(ench, out.getOrDefault(ench, 0), e.getValue()));
        }
        return new EnchItem(out, resultRepairCost(target.repairCost, sacrifice.repairCost),
            target.isBook && sacrifice.isBook);
    }

    /** Total experience POINTS required to reach a given XP level from 0 (vanilla curve). */
    public static int xpForLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    /** Two equal levels combine to one higher; otherwise the higher wins. Capped at the enchant max. */
    private static int finalLevel(String ench, int targetLevel, int sacLevel) {
        int lvl = (targetLevel == sacLevel) ? sacLevel + 1 : Math.max(targetLevel, sacLevel);
        return Math.min(lvl, maxLevel(ench));
    }
}
