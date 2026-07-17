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

    /**
     * Horizontal distance from the bot to the (overworld) trip target — the length of a direct overworld leg.
     * The overworld-direct vs nether-routing decision keys off THIS (how far the bot is from the destination),
     * not the target's distance from spawn: a bot already parked out at a deep base must fly the short hop in,
     * not route millions of blocks back through the nether from 0,0.
     */
    static double directLegDistance() {
        var c = cfg();
        var p = CACHE.getPlayerCache();
        return Math.hypot(c.tripTargetX - p.getX(), c.tripTargetZ - p.getZ());
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
    /** Total elytras carried = the worn one + any spares in the inventory (each elytra is a single, unstacked item). */
    static int elytraCount() { return (elytraWorn() ? 1 : 0) + countItems(FlightGear::isElytra, false); }

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

    /** Count-only satisfaction (ignores the offhand-equip requirement, which is a separate equip-step concern
     *  handled elsewhere) — used by {@link Regear}'s e-bounce cherry-pick to decide whether it's worth pulling
     *  more food/totems while it's already stopped at the ender chest for an elytra top-up. */
    static boolean egapCountSatisfied()  { return egapCount() >= minEgaps(); }
    static boolean totemCountSatisfied() { return totemCount() >= minTotems(); }

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

    // Checklist minimums come from the assigned flight kit profile ({@code elytraPilot.flightKitProfile}) when one is
    // set, else the legacy {@code preflight*} fields — so the flight "kit" (counts included) is editable in one place.
    private static int minArmor()        { var p = flightProfile(); return p != null ? p.minArmor    : cfg().preflightMinArmor; }
    private static int minElytrasBase()  { var p = flightProfile(); return p != null ? p.minElytras  : cfg().preflightMinElytras; }
    private static int minTotems()       { var p = flightProfile(); return p != null ? p.minTotems   : cfg().preflightMinTotems; }
    private static boolean reqOffhand()  { var p = flightProfile(); return p != null ? p.offhandTotemRequired : cfg().preflightOffhandTotem; }
    private static int minFireworksBase(){ var p = flightProfile(); return p != null ? p.minFireworks : cfg().preflightMinFireworks; }
    private static int minEgaps()        { var p = flightProfile(); return p != null ? p.minEgaps     : cfg().preflightMinEgaps; }
    private static boolean reqPickaxe()  { var p = flightProfile(); return p != null ? p.requirePickaxe : cfg().preflightRequirePickaxe; }
    private static boolean wantWeapon()  { var p = flightProfile(); return p != null ? p.wantWeapon   : cfg().preflightWantWeapon; }
    private static int minEchests()      { var p = flightProfile(); return p != null ? p.minEchests   : cfg().preflightMinEchests; }
    private static com.aquarius.util.config.Config.Client.Extra.KitProfile flightProfile() {
        return CONFIG.client.extra.kitProfile(CONFIG.client.extra.elytraPilot.flightKitProfile);
    }

    /**
     * Required firework count for the current trip. When {@code tripEstimateFireworks} is on and this is an
     * OVERWORLD-DIRECT trip (within the spawn region, not a nether destination), size it to the leg distance via
     * the climb-glide economy: {@code ceil(dist / (climbAltPerRocket × cruiseGlideRatio) × fireworkSafetyMargin)},
     * floored at {@code preflightMinFireworks}. Nether-routed legs aren't modeled — they keep the flat minimum.
     */
    static int requiredFireworks() {
        var c = cfg();
        int base = minFireworksBase();
        if (!c.tripEstimateFireworks || !c.tripActive || c.tripTargetIsNether) return base;
        double dist = directLegDistance();
        if (dist > c.spawnRegionRadius) return base;   // nether-routed transit: not modeled here
        double perRocket = Math.max(1.0, c.climbAltPerRocket * c.cruiseGlideRatio);
        int est = (int) Math.ceil(dist / perRocket * c.fireworkSafetyMargin);
        return Math.max(base, est);
    }

    /**
     * Required elytra count (worn + spares) for the trip. An elytra lasts only ~432s of flight, so a long leg
     * outlasts one — the gear-up pulls this many and the mid-flight {@code swapElytra} redeploys them as they wear
     * out. Overworld-direct trips size it to the leg distance ({@code ceil(dist/elytraBlocksPerElytra × margin)});
     * nether-routed legs keep the flat {@code preflightMinElytras}.
     */
    static int requiredElytras() {
        var c = cfg();
        int base = Math.max(1, minElytrasBase());
        if (!c.tripEstimateFireworks || !c.tripActive || c.tripTargetIsNether) return base;
        double dist = directLegDistance();
        if (dist > c.spawnRegionRadius) return base;
        int est = (int) Math.ceil(dist / Math.max(1.0, c.elytraBlocksPerElytra) * c.fireworkSafetyMargin);
        return Math.max(base, est);
    }

    /**
     * True if ANY required checklist category is still short (the optional weapon never counts). Unlike
     * {@link #ready()} this ignores {@link #elytraWorn()} — worn-vs-carried is an equip-step concern, not a
     * sourcing one. Drives {@link Regear}'s cherry-pick fallback: "is it worth opening another shulker?"
     */
    static boolean anyDeficit() {
        return elytraCount() < requiredElytras()
            || armorPiecesAnywhere() < minArmor()
            || totemCount() < minTotems()
            || (reqOffhand() && !offhandTotem())
            || fireworkCount() < requiredFireworks()
            || egapCount() < minEgaps()
            || (reqPickaxe() && !hasPickaxe())
            || echestCount() < minEchests();
    }

    /** All REQUIRED checks pass (the sword/axe is optional and never gates). */
    static boolean ready() {
        var c = cfg();
        return elytraWorn()
            && elytraCount() >= requiredElytras()
            && armorPiecesAnywhere() >= minArmor()
            && totemCount() >= minTotems()
            && (!reqOffhand() || offhandTotem())
            && fireworkCount() >= requiredFireworks()
            && egapCount() >= minEgaps()
            && (!reqPickaxe() || hasPickaxe())
            && echestCount() >= minEchests();
    }

    /** One-line-per-check status, for the pre-flight log. */
    static String report() {
        var c = cfg();
        StringBuilder b = new StringBuilder("Pre-flight check:\n");
        int needEl = requiredElytras();
        mark(b, "elytra+armor", elytraWorn() && elytraCount() >= needEl && armorPiecesAnywhere() >= minArmor(),
            (elytraWorn() ? elytraCount() + "/" + needEl + " elytra" : "NO elytra worn")
                + (needEl > minElytrasBase() ? " (trip est.)" : "")
                + " + " + armorPiecesAnywhere() + "/" + minArmor() + " armor");
        mark(b, "totems", totemCount() >= minTotems() && (!reqOffhand() || offhandTotem()),
            totemCount() + "/" + minTotems() + (offhandTotem() ? ", offhand ok" : ", offhand EMPTY"));
        int needFw = requiredFireworks();
        mark(b, "fireworks", fireworkCount() >= needFw, fireworkCount() + "/" + needFw
            + (needFw > minFireworksBase() ? " (trip estimate)" : ""));
        mark(b, "egaps", egapCount() >= minEgaps(), egapCount() + "/" + minEgaps());
        mark(b, "pickaxe", !reqPickaxe() || hasPickaxe(), hasPickaxe() ? "ok" : "missing");
        mark(b, "weapon (opt)", true, hasWeapon() ? "ok" : "MISSING (optional)");
        mark(b, "echests", echestCount() >= minEchests(), echestCount() + "/" + minEchests());
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
        if (isElytra(candidate))   return elytraCount() < requiredElytras();
        if (isOtherArmor(candidate)) return armorPiecesAnywhere() < minArmor();
        if (isTotem(candidate))    return totemCount() < minTotems();
        if (isFirework(candidate)) return fireworkCount() < requiredFireworks();
        if (isEgap(candidate))     return egapCount() < minEgaps();
        if (isPickaxe(candidate))  return reqPickaxe() && !hasPickaxe();
        if (isWeapon(candidate))   return wantWeapon() && !hasWeapon();
        if (isEchest(candidate))   return echestCount() < minEchests();
        return false;
    }
}
