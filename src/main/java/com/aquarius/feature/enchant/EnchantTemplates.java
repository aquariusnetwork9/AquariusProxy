package com.aquarius.feature.enchant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Built-in "max" enchant loadouts per gear family for the {@link com.aquarius.module.impl.Enchanter}.
 *
 * <p>Each family ({@code sword}, {@code pickaxe}, {@code helmet}, {@code bow}, ...) maps to a
 * {@link Template}: a set of always-applied {@code fixed} enchants plus zero or more
 * {@link VariantGroup}s — mutually-exclusive choices the operator picks (e.g. a sword's
 * <i>damage</i> = sharpness | smite | bane; a tool's <i>yield</i> = fortune | silk_touch). The
 * chosen-or-default option of every group is merged into the fixed set to produce the final target
 * enchant map for an item.
 *
 * <p>All bundled templates are pre-validated self-consistent against {@link EnchantCost}'s
 * incompatibility groups (within a template only one option of each exclusive group is ever active).
 */
public final class EnchantTemplates {
    private EnchantTemplates() {}

    /** A mutually-exclusive set of choices for one slot of a template (e.g. sword damage type). */
    public static final class VariantGroup {
        public final String name;                       // e.g. "sword_damage"
        public final String defaultChoice;              // e.g. "sharpness"
        public final Map<String, Map<String, Integer>> options;  // choice -> (enchant -> level)

        VariantGroup(String name, String defaultChoice, Map<String, Map<String, Integer>> options) {
            this.name = name;
            this.defaultChoice = defaultChoice;
            this.options = options;
        }

        public List<String> choices() { return new ArrayList<>(options.keySet()); }
    }

    /** A gear family's max loadout: fixed enchants + the variant groups. */
    public static final class Template {
        public final String family;
        public final Map<String, Integer> fixed;
        public final List<VariantGroup> variants;

        Template(String family, Map<String, Integer> fixed, List<VariantGroup> variants) {
            this.family = family;
            this.fixed = fixed;
            this.variants = variants;
        }
    }

    private static final Map<String, Template> TEMPLATES = new LinkedHashMap<>();

    /** All known variant-group names -> their template family (for the command + validation). */
    private static final Map<String, String> GROUP_FAMILY = new LinkedHashMap<>();

    /**
     * Curse short-name -> registry enchant name. Curses are independent, per-family <b>opt-in</b> toggles —
     * never applied by default and never bundled into the fixed/variant sets. Enable per family with
     * {@code .enc curse <family> <vanishing|binding> on}.
     */
    private static final Map<String, String> CURSE_ENCH = new LinkedHashMap<>();
    static {
        CURSE_ENCH.put("vanishing", "vanishing_curse"); // every enchantable item can hold Curse of Vanishing
        CURSE_ENCH.put("binding", "binding_curse");     // only worn gear (armor slots + elytra)
    }

    /** Families that can carry Curse of Binding (worn in an armor slot). Vanishing applies to every family. */
    private static final Set<String> BINDABLE = Set.of("helmet", "chestplate", "leggings", "boots", "elytra");

    private static Map<String, Integer> ench(Object... pairs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], (Integer) pairs[i + 1]);
        return m;
    }

    @SafeVarargs
    private static VariantGroup variant(String name, String def, Map.Entry<String, Map<String, Integer>>... opts) {
        Map<String, Map<String, Integer>> options = new LinkedHashMap<>();
        for (var e : opts) options.put(e.getKey(), e.getValue());
        return new VariantGroup(name, def, options);
    }

    private static Map.Entry<String, Map<String, Integer>> opt(String key, Map<String, Integer> enchants) {
        return Map.entry(key, enchants);
    }

    private static void reg(String family, Map<String, Integer> fixed, VariantGroup... variants) {
        TEMPLATES.put(family, new Template(family, fixed, List.of(variants)));
        for (VariantGroup v : variants) GROUP_FAMILY.put(v.name, family);
    }

    static {
        reg("sword", ench("unbreaking", 3, "mending", 1, "looting", 3, "fire_aspect", 2,
                "knockback", 2, "sweeping_edge", 3),
            variant("sword_damage", "sharpness",
                opt("sharpness", ench("sharpness", 5)),
                opt("smite", ench("smite", 5)),
                opt("bane_of_arthropods", ench("bane_of_arthropods", 5))));

        // pickaxe / shovel / hoe share the pure-tool loadout
        VariantGroup toolYield = variant("tool_yield", "fortune",
            opt("fortune", ench("fortune", 3)),
            opt("silk_touch", ench("silk_touch", 1)));
        reg("pickaxe", ench("efficiency", 5, "unbreaking", 3, "mending", 1), toolYield);
        reg("shovel", ench("efficiency", 5, "unbreaking", 3, "mending", 1),
            variant("tool_yield", "fortune",
                opt("fortune", ench("fortune", 3)),
                opt("silk_touch", ench("silk_touch", 1))));
        reg("hoe", ench("efficiency", 5, "unbreaking", 3, "mending", 1),
            variant("tool_yield", "fortune",
                opt("fortune", ench("fortune", 3)),
                opt("silk_touch", ench("silk_touch", 1))));

        // axe is also a weapon — tool yield + an optional damage variant
        reg("axe", ench("efficiency", 5, "unbreaking", 3, "mending", 1),
            variant("axe_yield", "fortune",
                opt("fortune", ench("fortune", 3)),
                opt("silk_touch", ench("silk_touch", 1))),
            variant("axe_damage", "sharpness",
                opt("sharpness", ench("sharpness", 5)),
                opt("smite", ench("smite", 5)),
                opt("bane_of_arthropods", ench("bane_of_arthropods", 5))));

        VariantGroup armorProt = variant("armor_protection", "protection",
            opt("protection", ench("protection", 4)),
            opt("blast_protection", ench("blast_protection", 4)),
            opt("fire_protection", ench("fire_protection", 4)),
            opt("projectile_protection", ench("projectile_protection", 4)));

        reg("helmet", ench("unbreaking", 3, "mending", 1, "respiration", 3, "aqua_affinity", 1, "thorns", 3),
            armorProt);
        reg("chestplate", ench("unbreaking", 3, "mending", 1, "thorns", 3),
            variant("armor_protection", "protection",
                opt("protection", ench("protection", 4)),
                opt("blast_protection", ench("blast_protection", 4)),
                opt("fire_protection", ench("fire_protection", 4)),
                opt("projectile_protection", ench("projectile_protection", 4))));
        reg("leggings", ench("unbreaking", 3, "mending", 1, "thorns", 3, "swift_sneak", 3),
            variant("armor_protection", "protection",
                opt("protection", ench("protection", 4)),
                opt("blast_protection", ench("blast_protection", 4)),
                opt("fire_protection", ench("fire_protection", 4)),
                opt("projectile_protection", ench("projectile_protection", 4))));
        reg("boots", ench("unbreaking", 3, "mending", 1, "thorns", 3, "feather_falling", 4, "soul_speed", 3),
            variant("armor_protection", "protection",
                opt("protection", ench("protection", 4)),
                opt("blast_protection", ench("blast_protection", 4)),
                opt("fire_protection", ench("fire_protection", 4)),
                opt("projectile_protection", ench("projectile_protection", 4))),
            variant("boots_walk", "depth_strider",
                opt("depth_strider", ench("depth_strider", 3)),
                opt("frost_walker", ench("frost_walker", 2))));

        reg("bow", ench("power", 5, "unbreaking", 3, "flame", 1, "punch", 2),
            variant("bow_special", "infinity",
                opt("infinity", ench("infinity", 1)),
                opt("mending", ench("mending", 1))));
        reg("crossbow", ench("quick_charge", 3, "unbreaking", 3, "mending", 1),
            variant("crossbow_special", "multishot",
                opt("multishot", ench("multishot", 1)),
                opt("piercing", ench("piercing", 4))));
        reg("trident", ench("unbreaking", 3, "mending", 1, "impaling", 5),
            variant("trident_style", "throw",
                opt("throw", ench("loyalty", 3, "channeling", 1)),
                opt("riptide", ench("riptide", 3))));
        reg("fishing_rod", ench("unbreaking", 3, "mending", 1, "lure", 3, "luck_of_the_sea", 3));
        reg("mace", ench("unbreaking", 3, "mending", 1, "wind_burst", 3),
            variant("mace_impact", "density",
                opt("density", ench("density", 5)),
                opt("breach", ench("breach", 4))));
        reg("elytra", ench("unbreaking", 3, "mending", 1));
        reg("shield", ench("unbreaking", 3, "mending", 1));

        // remaining vanilla enchantable items (durability only, except shears which also takes efficiency)
        reg("shears", ench("efficiency", 5, "unbreaking", 3, "mending", 1));
        reg("flint_and_steel", ench("unbreaking", 3, "mending", 1));
        reg("carrot_on_a_stick", ench("unbreaking", 3, "mending", 1));
        reg("warped_fungus_on_a_stick", ench("unbreaking", 3, "mending", 1));
        reg("brush", ench("unbreaking", 3, "mending", 1));
    }

    /** The gear family for a registry item name (e.g. "diamond_sword" -> "sword"), or null if none. */
    public static String familyOf(String itemName) {
        if (itemName == null) return null;
        // exact single-item families first (so "crossbow" isn't caught by a "bow" suffix, etc.)
        switch (itemName) {
            case "bow", "crossbow", "trident", "fishing_rod", "mace", "elytra", "shield",
                 "shears", "flint_and_steel", "carrot_on_a_stick", "warped_fungus_on_a_stick", "brush" -> { return itemName; }
            default -> { }
        }
        if (itemName.endsWith("_pickaxe")) return "pickaxe";
        if (itemName.endsWith("_axe")) return "axe";
        if (itemName.endsWith("_shovel")) return "shovel";
        if (itemName.endsWith("_hoe")) return "hoe";
        if (itemName.endsWith("_sword")) return "sword";
        if (itemName.endsWith("_helmet")) return "helmet";
        if (itemName.endsWith("_chestplate")) return "chestplate";
        if (itemName.endsWith("_leggings")) return "leggings";
        if (itemName.endsWith("_boots")) return "boots";
        return null;
    }

    public static Template template(String family) { return TEMPLATES.get(family); }

    public static List<String> families() { return new ArrayList<>(TEMPLATES.keySet()); }

    /** The family that owns a variant-group name (e.g. "tool_yield" -> "pickaxe"), or null. */
    public static String groupFamily(String groupName) { return GROUP_FAMILY.get(groupName); }

    public static boolean isVariantGroup(String groupName) { return GROUP_FAMILY.containsKey(groupName); }

    /** The selectable curse short-names ("vanishing", "binding"). */
    public static List<String> curses() { return new ArrayList<>(CURSE_ENCH.keySet()); }

    /** Config key for a per-family curse toggle, e.g. {@code curseKey("boots", "binding")} = {@code "boots|binding"}. */
    public static String curseKey(String family, String curse) { return family + "|" + curse; }

    /** True if {@code family} can hold the curse {@code curse} ("vanishing" applies to all families, "binding" to worn gear). */
    public static boolean supportsCurse(String family, String curse) {
        if (family == null || !TEMPLATES.containsKey(family) || !CURSE_ENCH.containsKey(curse)) return false;
        return curse.equals("binding") ? BINDABLE.contains(family) : true;
    }

    /** The curses {@code family} can carry, in order (e.g. boots -> [vanishing, binding], sword -> [vanishing]). */
    public static List<String> supportedCurses(String family) {
        List<String> out = new ArrayList<>();
        for (String c : CURSE_ENCH.keySet()) if (supportsCurse(family, c)) out.add(c);
        return out;
    }

    /**
     * The resolved target enchant map for an item, applying the operator's per-group variant choices
     * (defaults where unset) plus any per-family curses toggled on. Returns null if {@code itemName} has
     * no template.
     */
    public static Map<String, Integer> forItem(String itemName, Map<String, String> variantChoices,
                                               Map<String, Boolean> curseChoices) {
        return resolve(familyOf(itemName), variantChoices, curseChoices);
    }

    /** The resolved enchant map for a family directly (no item name needed), applying variant + curse choices. */
    public static Map<String, Integer> resolveFamily(String family, Map<String, String> variantChoices,
                                                     Map<String, Boolean> curseChoices) {
        return resolve(family, variantChoices, curseChoices);
    }

    /** Shared resolver: fixed enchants + the chosen/default variant of each group + any opted-in curses. */
    private static Map<String, Integer> resolve(String family, Map<String, String> variantChoices,
                                                Map<String, Boolean> curseChoices) {
        if (family == null) return null;
        Template t = TEMPLATES.get(family);
        if (t == null) return null;
        Map<String, Integer> out = new LinkedHashMap<>(t.fixed);
        for (VariantGroup g : t.variants) {
            String choice = variantChoices == null ? null : variantChoices.get(g.name);
            Map<String, Integer> opt = g.options.get(choice);
            if (opt == null) opt = g.options.get(g.defaultChoice);
            if (opt != null) out.putAll(opt);
        }
        applyCurses(family, out, curseChoices);
        return out;
    }

    /** Add any per-family curse the operator has explicitly toggled on (never on by default). */
    private static void applyCurses(String family, Map<String, Integer> out, Map<String, Boolean> curseChoices) {
        if (curseChoices == null || curseChoices.isEmpty()) return;
        for (Map.Entry<String, String> e : CURSE_ENCH.entrySet()) {
            if (!supportsCurse(family, e.getKey())) continue;
            if (Boolean.TRUE.equals(curseChoices.get(curseKey(family, e.getKey())))) out.put(e.getValue(), 1);
        }
    }

    /** Pretty one-line description of a family's resolved template under the given choices. */
    public static String describe(String family, Map<String, String> variantChoices,
                                  Map<String, Boolean> curseChoices) {
        Template t = TEMPLATES.get(family);
        if (t == null) return family + ": (no template)";
        Map<String, Integer> resolved = resolve(family, variantChoices, curseChoices);
        StringBuilder sb = new StringBuilder(family).append(": ");
        boolean first = true;
        for (var e : resolved.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append(' ').append(e.getValue());
            first = false;
        }
        if (!t.variants.isEmpty()) {
            sb.append("  [");
            first = true;
            for (VariantGroup g : t.variants) {
                if (!first) sb.append("; ");
                String choice = variantChoices == null ? null : variantChoices.get(g.name);
                if (choice == null || !g.options.containsKey(choice)) choice = g.defaultChoice;
                sb.append(g.name).append('=').append(choice);
                first = false;
            }
            sb.append(']');
        }
        return sb.toString();
    }
}
