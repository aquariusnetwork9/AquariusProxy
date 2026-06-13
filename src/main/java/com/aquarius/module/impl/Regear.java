package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.cache.data.inventory.Container;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.inventory.InventoryActionRequest;
import com.aquarius.feature.inventory.util.InventoryActionMacros;
import com.aquarius.feature.inventory.util.InventoryUtil;
import com.aquarius.feature.pathfinder.goals.GoalNear;
import com.aquarius.mc.block.BlockPos;
import com.aquarius.mc.item.ItemData;
import com.aquarius.mc.item.ItemRegistry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.BARITONE;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.INVENTORY;

/**
 * Regear — resupply the bot from a pre-stocked "kit" shulker held in an ender chest.
 *
 * <p>Cycle: place the bot's own carried ender chest beside it (ender chests share one global inventory) — or,
 * if none is carried, walk to the nearest placed ender chest — open it, pull the named/coloured kit shulker,
 * close, place + open the kit shulker, take everything out, break and collect the now-empty shulker, return it
 * to the ender chest, recover the ender chest (silk touch, own only), then gear up: equip the kit's armour and
 * put a totem in the offhand. Runs once per enable (a one-shot), then optionally toggles itself off.
 *
 * <p>This is essentially the inverse of {@link AquariusMiner}'s storage cycle (pull a SPECIFIC shulker and empty
 * ALL of it, instead of filling an empty one with the haul). It reuses the same 2b-hardened container primitives
 * via {@link AbstractFieldModule}.
 */
public class Regear extends AbstractFieldModule {

    private enum State {
        IDLE, RELOCATE, ACQUIRE, PLACE_ECHEST, PATH_ECHEST, OPEN_ECHEST, PULL_KIT, CLOSE_ECHEST,
        PLACE_KIT, OPEN_KIT, EMPTY_KIT, CLOSE_KIT, BREAK_KIT,
        RETURN_OPEN, RETURN_DEPOSIT, RETURN_CLOSE, RECOVER_ECHEST, GEAR_UP, DONE
    }

    private State state = State.IDLE;
    private int step;
    private int timer;
    private int attempts;

    private boolean ownEchest;
    private @Nullable BlockPos echPos;
    private @Nullable ItemData echItem;
    private @Nullable GoalNear pathGoal;
    private @Nullable BlockPos shulkPos;
    private @Nullable ItemData kitShulkerItem;
    private @Nullable BlockPos avoidSpot;

    private boolean paused;
    private boolean complete;
    private boolean hazardPaused;
    private boolean flightRefill;   // set by ElytraTrip: pull ONLY items the flight checklist is missing

    private int gearArmorIdx;   // gear-up: which armour slot we're filling (0-3)
    private int relocateAttempts;  // self-kills spent looking for an open-sky spot with a reachable echest
    private boolean relocateForceKill;  // RELOCATE entered from a reach failure: self-kill, don't re-accept this spot
    private double pathBestDist;   // closest we've gotten to the echest this attempt (stuck detector)
    private int pathStuckTicks;    // ticks of no progress toward the echest

    /** ElytraTrip's pre-flight gear-up: pull only the items {@link FlightGear} reports as missing, not the whole kit. */
    public void setFlightRefill(boolean b) { flightRefill = b; }

    @Override
    public boolean enabledSetting() { return CONFIG.client.extra.regear.enabled; }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(ClientBotTick.Starting.class, this::onStarting)
        );
    }

    @Override
    public void onEnable() {
        paused = false; complete = false; hazardPaused = false;
        gearArmorIdx = 0;
        relocateAttempts = 0;
        relocateForceKill = false; pathBestDist = Double.MAX_VALUE; pathStuckTicks = 0;
        ownEchest = false; echPos = null; shulkPos = null; pathGoal = null; kitShulkerItem = null; avoidSpot = null;
        if (CONFIG.client.extra.regear.selfKillRelocate) {
            go(State.RELOCATE);
            info("Regear: starting - relocation enabled, scanning for an open-sky spot with a reachable echest.");
        } else {
            go(State.ACQUIRE);
            info("Regear: starting - looking for the kit shulker.");
        }
    }

    @Override
    public void onDisable() {
        if (BARITONE.isActive()) BARITONE.stop();
        restoreBreaking();
        state = State.IDLE;
        flightRefill = false;
        echPos = null; shulkPos = null; pathGoal = null; kitShulkerItem = null; avoidSpot = null;
    }

    private void onStarting(ClientBotTick.Starting event) {
        // (re)connected mid-cycle: safest to restart the whole cycle from a clean inventory.
        if (state != State.IDLE && !complete) { go(State.ACQUIRE); }
    }

    private void go(State s) { state = s; step = 0; timer = 0; attempts = 0; }

    private void abort(String reason) {
        if (BARITONE.isActive()) BARITONE.stop();
        restoreBreaking();
        paused = true;
        state = State.IDLE;
        flightRefill = false;
        warn("Regear paused: {}. Toggle /regear off/on to retry.", reason);
        inGameAlertActivePlayer("<red>Regear paused: " + reason);
    }

    private void finishOk() {
        restoreBreaking();
        complete = true;
        state = State.IDLE;
        flightRefill = false;
        info("Regear complete - geared up.");
        inGameAlertActivePlayer("<green>Regear complete");
        if (CONFIG.client.extra.regear.disableWhenDone) {
            CONFIG.client.extra.regear.enabled = false;
            syncEnabledFromConfig();
        }
    }

    // ---------------------------------------------------------------- tick

    private void onTick(ClientBotTick event) {
        var cfg = CONFIG.client.extra.regear;
        if (notReady()) return;
        if (state == State.IDLE || complete || paused) return;

        if (cfg.pauseOnPlayer && playerNearby(cfg.playerPauseRange)) {
            if (!hazardPaused) {
                hazardPaused = true;
                if (BARITONE.isActive()) BARITONE.stop();
                warn("Regear: player within {} blocks - pausing.", (int) cfg.playerPauseRange);
            }
            return;
        }
        if (hazardPaused) { hazardPaused = false; info("Regear: clear - resuming."); }

        if (timer > 0) { timer--; return; }

        switch (state) {
            case RELOCATE -> tickRelocate();
            case ACQUIRE -> tickAcquire();
            case PLACE_ECHEST -> tickPlaceEchest();
            case PATH_ECHEST -> tickPathEchest();
            case OPEN_ECHEST -> tickOpenEchest();
            case PULL_KIT -> tickPullKit();
            case CLOSE_ECHEST -> tickCloseThen(State.PLACE_KIT);
            case PLACE_KIT -> tickPlaceKit();
            case OPEN_KIT -> tickOpenKit();
            case EMPTY_KIT -> tickEmptyKit();
            case CLOSE_KIT -> tickCloseThen(State.BREAK_KIT);
            case BREAK_KIT -> tickBreakKit();
            case RETURN_OPEN -> tickReturnOpen();
            case RETURN_DEPOSIT -> tickReturnDeposit();
            case RETURN_CLOSE -> tickCloseThen(ownEchest ? State.RECOVER_ECHEST : State.GEAR_UP);
            case RECOVER_ECHEST -> tickRecoverEchest();
            case GEAR_UP -> tickGearUp();
            case DONE -> finishOk();
            default -> { }
        }
    }

    // ---------------------------------------------------------------- phases

    /**
     * Self-kill relocation: on a hostile 2b2t spawn the bot may land boxed into a lavacast or with no reachable
     * ender chest in range. Rather than abort, {@code /kill} and let AutoRespawn drop it at a fresh spawn point;
     * repeat until it lands somewhere with open sky (room to take off) AND an ender chest within scan range, then
     * gear up there. The ticks-skipped-while-dead gate in {@link #notReady()} means this state simply pauses
     * through each death and resumes scanning on the new spot.
     */
    private void tickRelocate() {
        var cfg = CONFIG.client.extra.regear;
        switch (step) {
            case 0 -> {
                // Entered from a reach failure (couldn't path / stuck): the echest here is unreachable, so don't
                // re-accept this spot — self-kill straight away. (Otherwise the scan below would keep saying "good
                // spot, echest in range" and we'd loop forever without progress.)
                if (relocateForceKill) { relocateForceKill = false; doSelfKill("echest here is unreachable"); return; }
                BlockPos pf = playerFeet();
                boolean sky = openSkyAbove(cfg.relocateMinSkyClearance);
                BlockPos ech = nearestBlock(cfg.echestScanRadius, pf.y() - 6, pf.y() + 6, n -> n.equals("ender_chest"));
                if (sky && ech != null) {
                    info("Relocate: good spot at {} - open sky + ender chest at {}. Gearing up.", pf, ech);
                    go(State.ACQUIRE);
                    return;
                }
                doSelfKill((!sky ? "boxed-in" : "") + (ech == null ? (sky ? "no echest in range" : " + no echest") : ""));
            }
            // waited through the death + respawn (ticks are skipped while dead); re-scan the new spot.
            default -> { step = 0; timer = cfg.actionDelayTicks; }
        }
    }

    /** Send /kill (capped) and wait for AutoRespawn; abort the cycle once the attempt cap is hit. */
    private void doSelfKill(String why) {
        var cfg = CONFIG.client.extra.regear;
        if (++relocateAttempts > cfg.relocateMaxAttempts) {
            abort("relocate gave up after " + (relocateAttempts - 1) + " self-kills (no open-sky spot with a reachable echest)");
            return;
        }
        warn("Relocate: {} - self-killing to respawn (attempt {}/{}).", why, relocateAttempts, cfg.relocateMaxAttempts);
        inGameAlertActivePlayer("<yellow>Regear relocate: self-kill " + relocateAttempts + "/" + cfg.relocateMaxAttempts + " (" + why + ")");
        sendClientPacketAsync(new ServerboundChatPacket("/kill"));
        step = 1; timer = cfg.relocateKillWaitTicks;
    }

    /** Decide the ender-chest source: place a carried one, else walk to a placed one nearby. */
    private void tickAcquire() {
        var cfg = CONFIG.client.extra.regear;
        int echSlot = InventoryUtil.searchPlayerInventory(this::isEnderChestItem);
        if (echSlot != -1) {
            ownEchest = true;
            echItem = ItemRegistry.REGISTRY.get(CACHE.getPlayerCache().getPlayerInventory().get(echSlot).getId());
            go(State.PLACE_ECHEST);
            return;
        }
        // fallback: no ender chest carried -> find the nearest placed one
        BlockPos p = playerFeet();
        BlockPos found = nearestBlock(cfg.echestScanRadius, p.y() - 6, p.y() + 6, n -> n.equals("ender_chest"));
        if (found == null) { failOrRelocate("no ender chest carried and none placed within " + cfg.echestScanRadius + " blocks", false); return; }
        ownEchest = false;
        echPos = found;
        pathGoal = pathToNear(found);
        pathBestDist = Double.MAX_VALUE; pathStuckTicks = 0;   // reset the stuck detector for this approach
        info("Regear: no ender chest carried - walking to the placed one at {}.", found);
        go(State.PATH_ECHEST);
    }

    /**
     * A failure that relocation can recover from: self-kill to a new spawn (if enabled), else abort the cycle.
     * {@code forceKill} = the failure proves the echest here is unreachable (couldn't path / stuck), so RELOCATE
     * must self-kill rather than re-accept this same spot.
     */
    private void failOrRelocate(String reason, boolean forceKill) {
        if (CONFIG.client.extra.regear.selfKillRelocate) {
            warn("Regear: {} - relocating.", reason);
            relocateForceKill = forceKill;
            go(State.RELOCATE);
        } else {
            abort(reason);
        }
    }

    /** Open a container with the ghost-hand when in range (no LOS needed), else the normal path-and-raytrace open. */
    private void openContainerGhostAware(@Nullable BlockPos pos) {
        var cfg = CONFIG.client.extra.regear;
        if (cfg.ghostInteract && pos != null && distToBot(pos) <= cfg.ghostReach) openGhost(pos);
        else open(pos);
    }

    /** True if {@code clearance} air blocks are clear directly above the bot's head (room to take off / not encased). */
    private boolean openSkyAbove(int clearance) {
        BlockPos pf = playerFeet();
        for (int dy = 2; dy < 2 + Math.max(1, clearance); dy++) {
            if (!isAir(new BlockPos(pf.x(), pf.y() + dy, pf.z()))) return false;
        }
        return true;
    }

    private void tickPlaceEchest() {
        var cfg = CONFIG.client.extra.regear;
        switch (step) {
            case 0 -> {
                echPos = selectSpotBeside(avoidSpot);
                if (echPos == null) { abort("no clear spot beside the bot to place the ender chest"); return; }
                if (BARITONE.isActive()) BARITONE.stop();
                place(echPos, echItem);
                timer = cfg.settleTicks; step = 1;
            }
            default -> {
                if (placed(echPos)) { avoidSpot = null; go(State.OPEN_ECHEST); }
                else if (++attempts >= 4) { abort("ender chest placement kept failing"); }
                else { avoidSpot = echPos; step = 0; timer = cfg.actionDelayTicks; }
            }
        }
    }

    private void tickPathEchest() {
        var cfg = CONFIG.client.extra.regear;
        double d = echPos == null ? Double.MAX_VALUE : distToBot(echPos);
        boolean ghostClose = cfg.ghostInteract && echPos != null && d <= cfg.ghostReach;
        if (arrivedAt(pathGoal) || ghostClose) {            // adjacent, OR close enough to ghost-open through a wall
            if (BARITONE.isActive()) BARITONE.stop();
            go(State.OPEN_ECHEST);
            return;
        }
        // Abandon clearly-impossible pathing: if we stop getting closer to the echest (stuck in a water flow, on an
        // isolated block, "no path found"), self-kill to relocate rather than grind. Cheaper than fighting terrain.
        if (d < pathBestDist - 0.5) { pathBestDist = d; pathStuckTicks = 0; }
        else { pathStuckTicks += cfg.actionDelayTicks; }
        if (pathStuckTicks >= cfg.relocateStuckTicks) {
            failOrRelocate("no progress toward the echest (stuck)", true);
        } else if (!BARITONE.isActive() && ++attempts > 3) {
            failOrRelocate("couldn't path to the placed ender chest", true);
        } else {
            timer = cfg.actionDelayTicks;
        }
    }

    private void tickOpenEchest() {
        var cfg = CONFIG.client.extra.regear;
        switch (step) {
            case 0 -> { openContainerGhostAware(echPos); timer = cfg.settleTicks; step = 1; }
            default -> {
                if (openContainerId() != 0) go(State.PULL_KIT);
                else if (++attempts >= 6) abort("ender chest wouldn't open");
                else { step = 0; timer = cfg.actionDelayTicks; }
            }
        }
    }

    /** Find the named/coloured kit shulker in the open echest and pull it into the inventory. */
    private void tickPullKit() {
        var cfg = CONFIG.client.extra.regear;
        if (openContainerId() == 0) { go(State.OPEN_ECHEST); return; }   // closed early -> reopen
        Container c = openContainer();
        if (c == null) { timer = cfg.actionDelayTicks; return; }
        if (findPlayerWindowSlot(c, this::isKitShulker) != -1) { go(State.CLOSE_ECHEST); return; } // already pulled
        int src = findContainerSlot(c, this::isKitShulker);
        if (src == -1) {
            abort(cfg.matchByColor ? "no " + cfg.kitShulkerColor + " kit shulker in the ender chest"
                                   : "no kit shulker named '" + cfg.kitShulkerName + "' in the ender chest");
            return;
        }
        if (!inventoryBusy()) shiftClick(c, src);
        timer = cfg.actionDelayTicks;
    }

    private void tickCloseThen(State next) {
        if (openContainerId() == 0) { go(next); return; }
        if (!inventoryBusy()) closeContainer();
        timer = CONFIG.client.extra.regear.actionDelayTicks;
        if (++attempts >= 10) go(next);   // assume closed if the server dropped the ack
    }

    private void tickPlaceKit() {
        var cfg = CONFIG.client.extra.regear;
        switch (step) {
            case 0 -> {
                int slot = findInInv(this::isKitShulker);
                if (slot == -1) { abort("lost the kit shulker after pulling it"); return; }
                kitShulkerItem = ItemRegistry.REGISTRY.get(CACHE.getPlayerCache().getPlayerInventory().get(slot).getId());
                shulkPos = selectSpotBeside(avoidSpot);
                if (shulkPos == null) { abort("no clear spot to place the kit shulker"); return; }
                place(shulkPos, kitShulkerItem);
                timer = cfg.settleTicks; step = 1;
            }
            default -> {
                if (placed(shulkPos)) { avoidSpot = null; go(State.OPEN_KIT); }
                else if (++attempts >= 4) abort("kit shulker placement kept failing");
                else { avoidSpot = shulkPos; step = 0; timer = cfg.actionDelayTicks; }
            }
        }
    }

    private void tickOpenKit() {
        var cfg = CONFIG.client.extra.regear;
        switch (step) {
            case 0 -> { openContainerGhostAware(shulkPos); timer = cfg.settleTicks; step = 1; }
            default -> {
                if (openContainerId() != 0) go(State.EMPTY_KIT);
                else if (++attempts >= 6) abort("kit shulker wouldn't open");
                else { step = 0; timer = cfg.actionDelayTicks; }
            }
        }
    }

    /** Shift everything out of the open kit shulker into the inventory, one per tick. */
    private void tickEmptyKit() {
        var cfg = CONFIG.client.extra.regear;
        if (openContainerId() == 0) { go(State.CLOSE_KIT); return; }   // already empty/closed
        Container c = openContainer();
        if (c == null) { timer = cfg.actionDelayTicks; return; }
        // Flight refill: pull ONLY items the pre-flight checklist is still short on (re-evaluated per pull, so
        // each category stops once satisfied). Normal regear: empty the whole kit.
        int src = flightRefill
            ? findContainerSlot(c, s -> s != Container.EMPTY_STACK && FlightGear.stillNeeds(s))
            : findContainerSlot(c, s -> s != Container.EMPTY_STACK);
        if (src == -1) { go(State.CLOSE_KIT); return; }                // shulker empty / all deficits met
        if (emptyMainSlots() <= 0 && countInInv(s -> s == Container.EMPTY_STACK) == 0) {
            abort("inventory full while emptying the kit"); return;
        }
        if (!inventoryBusy()) shiftClick(c, src);
        timer = cfg.actionDelayTicks;
    }

    /** Break the emptied kit shulker (any tool) and collect the dropped (named, empty) shulker. */
    private void tickBreakKit() {
        var cfg = CONFIG.client.extra.regear;
        if (placed(shulkPos)) {
            if (++attempts > 200) { abort("couldn't break the kit shulker"); return; }
            breakAt(shulkPos, true);   // continuous, every tick - no delay
            return;
        }
        // block gone -> chase + collect the dropped empty shulker (drops fly up to ~2 blocks on 2b)
        if (!BARITONE.isActive()) BARITONE.pickup();
        boolean collected = countInInv(this::isShulkerBox) > 0;
        if (collected || ++step > 60) {
            if (BARITONE.isActive()) BARITONE.stop();
            shulkPos = null; attempts = 0;
            if (CONFIG.client.extra.regear.returnShulker && collected) go(State.RETURN_OPEN);
            else go(ownEchest ? State.RECOVER_ECHEST : State.GEAR_UP);
        } else {
            timer = cfg.actionDelayTicks;
        }
    }

    /** Re-open the ender chest to put the empty shulker back. */
    private void tickReturnOpen() {
        var cfg = CONFIG.client.extra.regear;
        if (echPos == null || isAir(echPos)) { go(ownEchest ? State.RECOVER_ECHEST : State.GEAR_UP); return; }
        switch (step) {
            case 0 -> {
                if (!arrivedAt(new GoalNear(echPos, REACH_RANGE_SQ))) { pathGoal = pathToNear(echPos); timer = cfg.actionDelayTicks; return; }
                open(echPos); timer = cfg.settleTicks; step = 1;
            }
            default -> {
                if (openContainerId() != 0) go(State.RETURN_DEPOSIT);
                else if (++attempts >= 6) go(ownEchest ? State.RECOVER_ECHEST : State.GEAR_UP);   // give up returning; don't strand the cycle
                else { step = 0; timer = cfg.actionDelayTicks; }
            }
        }
    }

    private void tickReturnDeposit() {
        var cfg = CONFIG.client.extra.regear;
        if (openContainerId() == 0) { go(State.RETURN_OPEN); return; }
        Container c = openContainer();
        if (c == null) { timer = cfg.actionDelayTicks; return; }
        int src = findPlayerWindowSlot(c, this::isShulkerBox);
        if (src == -1) { go(State.RETURN_CLOSE); return; }   // nothing left to return
        if (!inventoryBusy()) shiftClick(c, src);
        timer = cfg.actionDelayTicks;
    }

    /** Break + recover the bot's own ender chest with a silk-touch pickaxe (so it drops as an ender chest). */
    private void tickRecoverEchest() {
        var cfg = CONFIG.client.extra.regear;
        if (echPos == null || isAir(echPos)) { go(State.GEAR_UP); return; }   // already recovered / never placed
        int silk = findSilkPick();
        if (silk == -1) { warn("Regear: no silk-touch pickaxe - leaving the ender chest placed."); go(State.GEAR_UP); return; }
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        ItemStack held = inv.get(36 + CACHE.getPlayerCache().getHeldItemSlot());
        if (!isSilkPick(held)) {                            // select the silk pick and WAIT for it to take
            if (!inventoryBusy()) holdItemAt(silk);
            timer = cfg.actionDelayTicks;
            if (++attempts >= 20) { warn("Regear: couldn't equip a silk pick - leaving the ender chest placed."); go(State.GEAR_UP); }
            return;
        }
        if (placed(echPos)) { breakAt(echPos, false); return; }   // silk held + auto-tool off -> recovers
        // recovered
        if (!BARITONE.isActive()) BARITONE.pickup();
        go(State.GEAR_UP);
    }

    /** Equip the kit's armour into empty armour slots, then a totem into the offhand. */
    private void tickGearUp() {
        var cfg = CONFIG.client.extra.regear;
        if ((cfg.equipArmor || cfg.equipElytra) && gearArmorIdx < ARMOR_EQUIP.length) {
            if (inventoryBusy()) { timer = cfg.actionDelayTicks; return; }
            final int idx = gearArmorIdx;
            // Chest slot in flight gear-up: equip an ELYTRA (not a chestplate), replacing a chestplate if one is
            // worn. Every other slot: the best matching armour piece into an empty slot, as usual.
            final boolean chestElytra = idx == 1 && cfg.equipElytra;
            var worn = CACHE.getPlayerCache().getEquipment(ARMOR_EQUIP[idx]);
            boolean wornIsElytra = worn != Container.EMPTY_STACK && ItemRegistry.REGISTRY.get(worn.getId()) == ItemRegistry.ELYTRA;
            boolean needFill = chestElytra ? !wornIsElytra : (cfg.equipArmor && worn == Container.EMPTY_STACK);
            if (needFill) {
                int piece = chestElytra
                    ? findInInv(s -> { String n = itemName(s); return n != null && n.equals("elytra"); })
                    : findInInv(s -> { String n = itemName(s); return n != null && n.endsWith(ARMOR_SUFFIX[idx]); });
                if (piece != -1) {
                    INVENTORY.submit(InventoryActionRequest.builder().owner(this)
                        .actions(InventoryActionMacros.swapSlots(piece, 5 + idx)).priority(ACTION_PRIORITY).build()); // armour slots 5..8
                    gearArmorIdx++; timer = cfg.actionDelayTicks; return;
                }
            }
            gearArmorIdx++; timer = cfg.actionDelayTicks; return;
        }
        if (cfg.offhandTotem) {
            if (inventoryBusy()) { timer = cfg.actionDelayTicks; return; }
            if (CACHE.getPlayerCache().getEquipment(EquipmentSlot.OFF_HAND) == Container.EMPTY_STACK) {
                int totem = findInInv(s -> matchesName(s, "totem_of_undying"));
                if (totem != -1) { moveToOffhand(totem); timer = cfg.actionDelayTicks; }
            }
        }
        go(State.DONE);
    }

    // ---------------------------------------------------------------- predicates / helpers

    private static final EquipmentSlot[] ARMOR_EQUIP =
        {EquipmentSlot.HELMET, EquipmentSlot.CHESTPLATE, EquipmentSlot.LEGGINGS, EquipmentSlot.BOOTS};
    private static final String[] ARMOR_SUFFIX = {"_helmet", "_chestplate", "_leggings", "_boots"};

    /** The kit shulker: a shulker box matched by colour prefix or by custom (anvil) name. */
    private boolean isKitShulker(@Nullable ItemStack s) {
        if (!isShulkerBox(s)) return false;
        var cfg = CONFIG.client.extra.regear;
        if (cfg.matchByColor && !cfg.kitShulkerColor.isBlank()) {
            String n = itemName(s);
            return n != null && n.startsWith(cfg.kitShulkerColor.toLowerCase() + "_");
        }
        String cn = customName(s);
        return cn != null && !cfg.kitShulkerName.isBlank()
            && cn.toLowerCase().contains(cfg.kitShulkerName.toLowerCase());
    }

    private int findSilkPick() {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 9; i <= 44; i++) if (isSilkPick(inv.get(i))) return i;
        return -1;
    }
    private boolean isSilkPick(@Nullable ItemStack s) {
        String n = itemName(s);
        if (n == null || !n.endsWith("pickaxe")) return false;
        var ench = s.getDataComponentsOrEmpty().get(DataComponentTypes.ENCHANTMENTS);
        return ench != null && ench.getEnchantments()
            .containsKey(com.aquarius.mc.enchantment.EnchantmentRegistry.SILK_TOUCH.get().id());
    }

    // ---------------------------------------------------------------- status

    public String statusLine() {
        if (complete) return "complete";
        if (paused) return "paused";
        if (state == State.IDLE) return "idle";
        return state.name().toLowerCase();
    }
    public boolean isPaused() { return paused; }
    public boolean isComplete() { return complete; }
    /** In the self-kill relocation loop (intentional deaths) — ElytraTrip uses this to not cancel the trip. */
    public boolean isRelocating() { return state == State.RELOCATE; }
}
