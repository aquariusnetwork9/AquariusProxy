package com.aquarius.module.impl;

import com.aquarius.cache.data.inventory.Container;
import com.aquarius.mc.item.ItemData;
import com.aquarius.mc.item.ItemRegistry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.List;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;

/**
 * Flight-readiness checklist + deficit logic, shared by {@link ElytraTrip} (pre-flight gating + logging) and
 * {@link Regear} (selective "refill only what's missing"). All counts are read live from the player cache:
 * "anywhere" counts include worn slots, the offhand, and the main inventory; the {@link #ready()} gate also
 * checks positional requirements (elytra in the chest, a totem in the offhand).
 *
 * <p>Checklist (minimums from {@code CONFIG.client.extra.elytraPilot.preflight*}): an elytra worn + N other
 * armour pieces; ≥N totems with one in the offhand; ≥1 stack of fireworks; ≥1 stack of enchanted golden apples;
 * a pickaxe; ≥N ender chests; and a sword/axe (optional — noted, never blocks).
 */
final class FlightGear {
    private FlightGear() {}

    private static com.aquarius.util.config.Config.Client.Extra.ElytraPilot cfg() {
        return CONFIG.client.extra.elytraPilot;
    }

    // ---------------------------------------------------------------- item predicates

    static boolean isElytra(ItemStack s)   { return is(s, "elytra"); }
    static boolean isTotem(ItemStack s)    { return is(s, "totem_of_undying"); }
    static boolean isFirework(ItemStack s) { return is(s, "firework_rocket"); }
    static boolean isEgap(ItemStack s)     { return is(s, "enchanted_golden_apple"); }
    static boolean isEchest(ItemStack s)   { return is(s, "ender_chest"); }
    static boolean isPickaxe(ItemStack s)  { String n = name(s); return n != null && n.endsWith("pickaxe"); }
    static boolean isWeapon(ItemStack s)   { String n = name(s); return n != null && (n.endsWith("sword") || n.endsWith("_axe")); }
    /** A wearable armour piece in the helmet/leggings/boots families (NOT the elytra, which lives in the chest). */
    static boolean isOtherArmor(ItemStack s) {
        String n = name(s);
        return n != null && (n.endsWith("_helmet") || n.endsWith("_leggings") || n.endsWith("_boots"));
    }

    private static boolean is(ItemStack s, String name) {
        String n = name(s);
        return n != null && n.equals(name);
    }
    private static String name(ItemStack s) {
        if (s == null || s == Container.EMPTY_STACK) return null;
        ItemData d = ItemRegistry.REGISTRY.get(s.getId());
        return d == null ? null : d.name();
    }

    // ---------------------------------------------------------------- counts (worn + offhand + inventory)

    private static List<ItemStack> inv() { return CACHE.getPlayerCache().getPlayerInventory(); }
    private static ItemStack worn(EquipmentSlot slot) { return CACHE.getPlayerCache().getEquipment(slot); }

    static boolean elytraWorn() { return isElytra(worn(EquipmentSlot.CHESTPLATE)); }

    /** Other armour pieces present anywhere (worn helmet/legs/boots + any armour piece carried in the inventory). */
    static int armorPiecesAnywhere() {
        int n = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HELMET, EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS})
            if (worn(s) != Container.EMPTY_STACK) n++;
        n += countItems(FlightGear::isOtherArmor, false);
        return n;
    }

    static boolean offhandTotem() { return isTotem(worn(EquipmentSlot.OFF_HAND)); }
    static int totemCount()       { return countItems(FlightGear::isTotem, true); }
    static int fireworkCount()    { return countItems(FlightGear::isFirework, false); }
    static int egapCount()        { return countItems(FlightGear::isEgap, false); }
    static int echestCount()      { return countItems(FlightGear::isEchest, false); }
    static boolean hasPickaxe()   { return countItems(FlightGear::isPickaxe, false) > 0; }
    static boolean hasWeapon()    { return countItems(FlightGear::isWeapon, false) > 0; }
    static boolean elytraAnywhere() { return elytraWorn() || countItems(FlightGear::isElytra, false) > 0; }

    /** Sum item quantity (not slots) over the inventory (9-44), optionally including the offhand. */
    private static int countItems(java.util.function.Predicate<ItemStack> pred, boolean includeOffhand) {
        int n = 0;
        List<ItemStack> inv = inv();
        for (int i = 9; i <= 44; i++) {
            ItemStack s = inv.get(i);
            if (s != Container.EMPTY_STACK && pred.test(s)) n += s.getAmount();
        }
        if (includeOffhand) {
            ItemStack off = worn(EquipmentSlot.OFF_HAND);
            if (off != Container.EMPTY_STACK && pred.test(off)) n += off.getAmount();
        }
        return n;
    }

    // ---------------------------------------------------------------- gating + reporting

    /**
     * Required firework count for the current trip. When {@code tripEstimateFireworks} is on and this is an
     * OVERWORLD-DIRECT trip (within the spawn region, not a nether destination), size it to the leg distance via
     * the climb-glide economy: {@code ceil(dist / (climbAltPerRocket × cruiseGlideRatio) × fireworkSafetyMargin)},
     * floored at {@code preflightMinFireworks}. Nether-routed legs aren't modeled — they keep the flat minimum.
     */
    static int requiredFireworks() {
        var c = cfg();
        int base = c.preflightMinFireworks;
        if (!c.tripEstimateFireworks || !c.tripActive || c.tripTargetIsNether) return base;
        double dist = Math.hypot(c.tripTargetX, c.tripTargetZ);
        if (dist > c.spawnRegionRadius) return base;   // nether-routed transit: not modeled here
        double perRocket = Math.max(1.0, c.climbAltPerRocket * c.cruiseGlideRatio);
        int est = (int) Math.ceil(dist / perRocket * c.fireworkSafetyMargin);
        return Math.max(base, est);
    }

    /** All REQUIRED checks pass (the sword/axe is optional and never gates). */
    static boolean ready() {
        var c = cfg();
        return elytraWorn()
            && armorPiecesAnywhere() >= c.preflightMinArmor
            && totemCount() >= c.preflightMinTotems
            && (!c.preflightOffhandTotem || offhandTotem())
            && fireworkCount() >= requiredFireworks()
            && egapCount() >= c.preflightMinEgaps
            && (!c.preflightRequirePickaxe || hasPickaxe())
            && echestCount() >= c.preflightMinEchests;
    }

    /** One-line-per-check status, for the pre-flight log. */
    static String report() {
        var c = cfg();
        StringBuilder b = new StringBuilder("Pre-flight check:\n");
        mark(b, "elytra+armor", elytraWorn() && armorPiecesAnywhere() >= c.preflightMinArmor,
            (elytraWorn() ? "elytra" : "NO elytra") + " + " + armorPiecesAnywhere() + "/" + c.preflightMinArmor + " armor");
        mark(b, "totems", totemCount() >= c.preflightMinTotems && (!c.preflightOffhandTotem || offhandTotem()),
            totemCount() + "/" + c.preflightMinTotems + (offhandTotem() ? ", offhand ok" : ", offhand EMPTY"));
        int needFw = requiredFireworks();
        mark(b, "fireworks", fireworkCount() >= needFw, fireworkCount() + "/" + needFw
            + (needFw > c.preflightMinFireworks ? " (trip estimate)" : ""));
        mark(b, "egaps", egapCount() >= c.preflightMinEgaps, egapCount() + "/" + c.preflightMinEgaps);
        mark(b, "pickaxe", !c.preflightRequirePickaxe || hasPickaxe(), hasPickaxe() ? "ok" : "missing");
        mark(b, "weapon (opt)", true, hasWeapon() ? "ok" : "MISSING (optional)");
        mark(b, "echests", echestCount() >= c.preflightMinEchests, echestCount() + "/" + c.preflightMinEchests);
        return b.toString().stripTrailing();
    }

    private static void mark(StringBuilder b, String label, boolean ok, String detail) {
        b.append(ok ? "  [OK] " : "  [X]  ").append(label).append(": ").append(detail).append('\n');
    }

    // ---------------------------------------------------------------- selective refill

    /**
     * Would pulling this kit item help meet a still-unmet "have" deficit? Used by Regear's flight-refill so it
     * tops up only what the checklist is short on and leaves the rest of the kit in the shulker. Re-evaluated
     * per call against the live inventory, so each pull naturally stops a category once it's satisfied.
     */
    static boolean stillNeeds(ItemStack candidate) {
        var c = cfg();
        if (isElytra(candidate))   return !elytraAnywhere();
        if (isOtherArmor(candidate)) return armorPiecesAnywhere() < c.preflightMinArmor;
        if (isTotem(candidate))    return totemCount() < c.preflightMinTotems;
        if (isFirework(candidate)) return fireworkCount() < requiredFireworks();
        if (isEgap(candidate))     return egapCount() < c.preflightMinEgaps;
        if (isPickaxe(candidate))  return c.preflightRequirePickaxe && !hasPickaxe();
        if (isWeapon(candidate))   return c.preflightWantWeapon && !hasWeapon();
        if (isEchest(candidate))   return echestCount() < c.preflightMinEchests;
        return false;
    }
}
