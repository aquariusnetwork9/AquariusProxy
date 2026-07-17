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

import com.aquarius.util.config.Config.Client.Extra.KitProfile;

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
        RETURN_OPEN, RETURN_DEPOSIT, RETURN_CLOSE, CHERRY_CHECK, RECOVER_ECHEST, GEAR_UP, DONE
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
    private boolean elytraRefill;   // set by ElytraPilot: refill ONLY elytras (fresh in, spent back to the kit)
    private int elytraRefillTarget; // target count of fresh elytras to hold in the inventory after the refill
    private int mendBottleTarget;   // elytraRefill only: also pull XP bottles up to this count (0 = don't bother)

    private int gearArmorIdx;   // gear-up: which armour slot we're filling (0-3)
    private int cherryPickAttempts;   // extra shulkers opened by the cherry-pick fallback beyond the primary kit shulker
    private int relocateAttempts;  // self-kills spent looking for an open-sky spot with a reachable echest
    private boolean relocateForceKill;  // RELOCATE entered from a reach failure: self-kill, don't re-accept this spot
    private double pathBestDist;   // closest we've gotten to the echest this attempt (stuck detector)
    private int pathStuckTicks;    // ticks of no progress toward the echest

    /** ElytraTrip's pre-flight gear-up: pull only the items {@link FlightGear} reports as missing, not the whole kit. */
    public void setFlightRefill(boolean b) { flightRefill = b; }

    /** ElytraPilot's e-bounce resupply: refill ONLY elytras — pull FRESH ones from the kit until the inventory holds
     *  {@code target}, dumping the SPENT ones back into the kit. The worn (armor) elytra is never touched. */
    public void setElytraRefill(boolean b, int target) { elytraRefill = b; elytraRefillTarget = target; }

    /** ElytraPilot's e-bounce Mending repair: while already sourcing elytras, ALSO pull XP bottles up to
     *  {@code target} (0 = don't bother) — sourcing only, ElytraPilot owns the actual throw once Regear completes. */
    public void setMendBottleTarget(int target) { mendBottleTarget = target; }

    // --- kit-profile override: temporarily source the shulker-match + equip flags from a named KitProfile ---
    private record RegearSnapshot(String name, boolean matchColor, String color, boolean contents,
                                  boolean elytraCt, int elytraN, boolean armor, boolean elytra, boolean totem, boolean ret) {}
    private @Nullable RegearSnapshot savedProfile;

    /**
     * Drive this Regear cycle from a named kit profile (a flow points at one via its assignment field): snapshot the
     * base {@code regear} config, then apply the profile's shulker-match + equip flags onto it. The validated state
     * machine keeps reading the same {@code cfg.*} fields — it just sees the profile's values. {@link #popProfile()}
     * (called on disable) restores the base config. No-op if {@code p} is null (→ legacy behaviour) or already pushed.
     */
    public void pushProfile(@Nullable KitProfile p) {
        if (p == null || savedProfile != null) return;
        var c = CONFIG.client.extra.regear;
        savedProfile = new RegearSnapshot(c.kitShulkerName, c.matchByColor, c.kitShulkerColor, c.matchByContents,
            c.matchByElytraCount, c.kitElytraCount, c.equipArmor, c.equipElytra, c.offhandTotem, c.returnShulker);
        c.kitShulkerName = p.shulkerName; c.matchByColor = p.matchByColor; c.kitShulkerColor = p.shulkerColor;
        c.matchByContents = p.matchByContents; c.matchByElytraCount = p.matchByElytraCount; c.kitElytraCount = p.elytraCount;
        c.equipArmor = p.equipArmor; c.equipElytra = p.equipElytra; c.offhandTotem = p.offhandTotem; c.returnShulker = p.returnShulker;
    }

    /** Restore the base regear config snapshotted by {@link #pushProfile}. Safe to call when none is active. */
    public void popProfile() {
        if (savedProfile == null) return;
        var c = CONFIG.client.extra.regear;
        var s = savedProfile;
        savedProfile = null;
        c.kitShulkerName = s.name(); c.matchByColor = s.matchColor(); c.kitShulkerColor = s.color(); c.matchByContents = s.contents();
        c.matchByElytraCount = s.elytraCt(); c.kitElytraCount = s.elytraN(); c.equipArmor = s.armor(); c.equipElytra = s.elytra();
        c.offhandTotem = s.totem(); c.returnShulker = s.ret();
    }

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
        // Standalone (spawn) regear: apply the assigned kit profile. No-op if a flow (ebounce resupply / trip gear-up)
        // already pushed its own profile, or if regear.profile is blank (→ legacy fields).
        pushProfile(CONFIG.client.extra.kitProfile(CONFIG.client.extra.regear.profile));
        paused = false; complete = false; hazardPaused = false;
        gearArmorIdx = 0;
        cherryPickAttempts = 0;
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
        popProfile();                 // restore the base regear config if a flow pushed a kit profile for this cycle
        state = State.IDLE;
        flightRefill = false;
        elytraRefill = false;
        mendBottleTarget = 0;
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
        elytraRefill = false;
        mendBottleTarget = 0;
        warn("Regear paused: {}. Toggle /regear off/on to retry.", reason);
        inGameAlertActivePlayer("<red>Regear paused: " + reason);
    }

    private void finishOk() {
        restoreBreaking();
        complete = true;
        state = State.IDLE;
        flightRefill = false;
        elytraRefill = false;
        mendBottleTarget = 0;
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
            case RETURN_CLOSE -> tickCloseThen(State.CHERRY_CHECK);
            case CHERRY_CHECK -> tickCherryCheck();
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

    /**
     * Find the shulker to pull into the inventory. Round 0 tries the primary named/coloured/contents-matched
     * "kit" shulker first, exactly as before — but most echests aren't organised around one hand-packed kit;
     * they're separate single-item shulkers (an elytra shulker, a totem shulker, a rockets shulker, ...). So if
     * round 0's primary match comes up empty, it falls straight through to the SAME cherry-pick search a later
     * round would use ({@link #findRichestShulkerSlot} + {@link #cherryPickStillNeeds}: a read-only peek via each
     * shulker's CONTAINER component, matching purely on content — the RICHEST match wins, never name/colour)
     * instead of aborting, and marks this as a cherry-pick round from here on, so {@link #tickEmptyKit} pulls
     * only what's needed rather than dumping the whole shulker.
     */
    private void tickPullKit() {
        var cfg = CONFIG.client.extra.regear;
        if (openContainerId() == 0) { go(State.OPEN_ECHEST); return; }   // closed early -> reopen
        Container c = openContainer();
        if (c == null) { timer = cfg.actionDelayTicks; return; }
        if (findPlayerWindowSlot(c, this::isShulkerBox) != -1) { go(State.CLOSE_ECHEST); return; } // already pulled

        int src;
        if (cherryPickAttempts == 0) {
            src = cfg.matchByContents ? findBestKitShulkerSlot(c) : findContainerSlot(c, this::isKitShulker);
            if (src == -1 && cfg.cherryPickFallback) {
                // Pick the RICHEST candidate (most matching items), not just the first one found - fewer, fatter
                // pulls beat visiting three half-empty shulkers, and matters most here since it's the common case
                // for an echest of separate single-item shulkers (an elytra shulker, a totem shulker, ...).
                src = findRichestShulkerSlot(c, this::cherryPickStillNeeds);
                if (src != -1) cherryPickAttempts = 1;
            }
        } else {
            src = findRichestShulkerSlot(c, this::cherryPickStillNeeds);
        }

        if (src == -1) {
            if (cherryPickAttempts > 0) {   // cherry-pick found nothing more - proceed with what's already gathered
                info("Regear: cherry-pick found no more shulkers with missing items - continuing with what's gathered.");
                go(ownEchest ? State.RECOVER_ECHEST : State.GEAR_UP);
                return;
            }
            // Ender storage is shared across all echests, so a missing kit shulker can't be fixed by relocating —
            // abort so the user re-stocks it (don't suicide-loop looking for a kit that isn't in ender storage).
            String primary = cfg.matchByContents ? "no shulker matching the flight-kit contents (elytra + fireworks)"
                : cfg.matchByColor ? "no " + cfg.kitShulkerColor + " kit shulker"
                                   : "no kit shulker named '" + cfg.kitShulkerName + "'";
            abort(primary + " in the ender chest" + (cfg.cherryPickFallback
                ? ", and cherry-pick found no other shulker covering what's needed either" : ""));
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
                // isShulkerBox, not isKitShulker: a cherry-pick round's pull won't match the kit-selection
                // criteria. Safe because CHERRY_CHECK guarantees the inventory holds at most one shulker box
                // at a time between rounds (see its javadoc).
                int slot = findInInv(this::isShulkerBox);
                if (slot == -1) { abort("lost the shulker after pulling it"); return; }
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
        // E-bounce elytra refill: dump SPENT elytras from the inventory back into the kit, then pull FRESH elytras
        // from the kit until the inventory holds the target count. The worn elytra (armor slot 6) is outside the
        // 9-44 inventory range so it is never touched. One action per tick.
        if (elytraRefill) {
            if (inventoryBusy()) { timer = cfg.actionDelayTicks; return; }
            int spent = findPlayerWindowSlot(c, this::isSpentElytra);     // worn-out spare -> back into the kit
            if (spent != -1) { shiftClick(c, spent); timer = cfg.actionDelayTicks; return; }
            if (countInInv(this::isFreshElytra) < elytraRefillTarget) {   // top up fresh spares to the target
                int fresh = findContainerSlot(c, this::isFreshElytra);
                if (fresh != -1 && findEmptyPlayerWindowSlot(c) != -1) { shiftClick(c, fresh); timer = cfg.actionDelayTicks; return; }
            }
            if (mendBottleTarget > 0 && countInInv(this::isXpBottle) < mendBottleTarget) {   // ElytraPilot will throw these once we're done
                int bottle = findContainerSlot(c, this::isXpBottle);
                if (bottle != -1 && findEmptyPlayerWindowSlot(c) != -1) { shiftClick(c, bottle); timer = cfg.actionDelayTicks; return; }
            }
            // Elytra/bottle needs (if any) are covered from THIS shulker - also grab food/totems it happens to hold
            // if the flight checklist is short on them (elytras/food/totems are what actually deplete over a long
            // e-bounce trip; armor/pickaxe/echest don't, so this deliberately stays narrow - see cherryPickStillNeeds).
            int other = findContainerSlot(c, s -> (FlightGear.isEgap(s) || FlightGear.isTotem(s)) && FlightGear.stillNeeds(s));
            if (other != -1 && findEmptyPlayerWindowSlot(c) != -1) { shiftClick(c, other); timer = cfg.actionDelayTicks; return; }
            go(State.CLOSE_KIT);   // nothing left this shulker can help with
            return;
        }
        // Flight refill (round 0) and every cherry-pick round (any mode): pull ONLY items still short somewhere
        // (re-evaluated per pull, so each category stops once satisfied). Normal regear's primary shulker
        // (round 0, no flags): empty it completely, as before.
        int src = (flightRefill || cherryPickAttempts > 0)
            ? findContainerSlot(c, s -> s != Container.EMPTY_STACK && cherryPickStillNeeds(s))
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
            go(State.CHERRY_CHECK);   // CHERRY_CHECK owns the return-or-keep-carried decision
        } else {
            timer = cfg.actionDelayTicks;
        }
    }

    /** Re-open the ender chest to put the empty shulker back. Failure here always gives up on returning AND on
     *  any further cherry-picking (an unreachable echest can't be reopened for another pull either) - it must
     *  NOT route back through {@link #tickCherryCheck}, which would just retry this and loop forever. */
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

    /**
     * The hub every kit-shulker cycle passes through after {@link #tickBreakKit} (whether or not a shulker was
     * actually collected) AND again after a successful {@link #tickReturnDeposit}/RETURN_CLOSE - both re-derive
     * the same decision from live state, which is what makes it safe to re-enter: whether to put the current
     * shulker back, and whether to open another one.
     *
     * <p>Return-or-keep-carried: if we're about to cherry-pick again, the shulker is ALWAYS returned first (a
     * clean inventory is what lets {@link #tickPlaceKit} unambiguously find "this round's" shulker via
     * {@code isShulkerBox}); otherwise (this is the last shulker of the cycle) the user's
     * {@link com.aquarius.util.config.Config.Client.Extra.Regear#returnShulker} preference governs, exactly as
     * the single-shulker cycle always did.
     *
     * <p>Loop-or-stop: keep cherry-picking while it's enabled, the attempt budget isn't spent, and the current
     * gear-up mode ({@link #cherryPickSatisfied}) still reports a deficit. The ender chest is never recovered
     * mid-loop (still placed/reachable at {@code echPos}), so looping back just reopens it via {@link #tickPullKit}'s
     * round-aware selection.
     */
    private void tickCherryCheck() {
        var cfg = CONFIG.client.extra.regear;
        boolean willContinue = cfg.cherryPickFallback && cherryPickAttempts < cfg.cherryPickMaxShulkers && !cherryPickSatisfied();
        if (countInInv(this::isShulkerBox) > 0 && (willContinue || cfg.returnShulker)) { go(State.RETURN_OPEN); return; }
        if (!willContinue) { go(ownEchest ? State.RECOVER_ECHEST : State.GEAR_UP); return; }
        cherryPickAttempts++;
        shulkPos = null; kitShulkerItem = null;
        info("Regear: still short after {} shulker(s) - cherry-picking another from the ender chest ({}/{}).",
            cherryPickAttempts, cherryPickAttempts, cfg.cherryPickMaxShulkers);
        go(State.OPEN_ECHEST);
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

    /** The kit shulker: matched by CONTENTS (flight kit), else by colour prefix, else by custom (anvil) name. */
    private boolean isKitShulker(@Nullable ItemStack s) {
        if (!isShulkerBox(s)) return false;
        var cfg = CONFIG.client.extra.regear;
        if (cfg.matchByElytraCount) return countElytrasIn(s) >= cfg.kitElytraCount;
        if (cfg.matchByContents) return kitContentsScore(s) >= 0;
        if (cfg.matchByColor && !cfg.kitShulkerColor.isBlank()) {
            String n = itemName(s);
            return n != null && n.startsWith(cfg.kitShulkerColor.toLowerCase() + "_");
        }
        String cn = customName(s);
        return cn != null && !cfg.kitShulkerName.isBlank()
            && cn.toLowerCase().contains(cfg.kitShulkerName.toLowerCase());
    }

    /**
     * Score a shulker by how much it looks like a flight kit, reading its CONTAINER contents (no opening). Returns
     * -1 if it isn't a flight kit (must contain an elytra AND fireworks); otherwise a higher score = more complete,
     * weighted by the preflight priority so the most complete kit wins when several qualify. Reuses {@link FlightGear}'s
     * item predicates (pickaxe = any material; armour = a non-chestplate piece).
     */
    private int kitContentsScore(@Nullable ItemStack shulker) {
        boolean elytra = false, fw = false, food = false, pick = false, armor = false, weapon = false, echest = false;
        for (ItemStack inner : containerContents(shulker)) {
            if (inner == null || inner == Container.EMPTY_STACK) continue;
            if (FlightGear.isElytra(inner)) elytra = true;
            else if (FlightGear.isFirework(inner)) fw = true;
            else if (FlightGear.isEgap(inner)) food = true;
            else if (FlightGear.isPickaxe(inner)) pick = true;
            else if (FlightGear.isOtherArmor(inner)) armor = true;
            else if (FlightGear.isWeapon(inner)) weapon = true;
            else if (FlightGear.isEchest(inner)) echest = true;
        }
        if (!(elytra && fw)) return -1;   // not a flight kit — needs at least the elytra + fireworks to fly
        return (elytra ? 64 : 0) + (fw ? 32 : 0) + (food ? 16 : 0) + (pick ? 8 : 0)
             + (armor ? 4 : 0) + (weapon ? 2 : 0) + (echest ? 1 : 0);
    }

    /** Contents mode: the echest slot holding the most-complete flight-kit shulker, or -1 if none qualifies. */
    private int findBestKitShulkerSlot(Container c) {
        int chestSlots = Math.max(0, c.getSize() - 36);
        int best = -1, bestScore = -1;
        for (int i = 0; i < chestSlots; i++) {
            ItemStack s = c.getItemStack(i);
            if (!isShulkerBox(s)) continue;
            int sc = kitContentsScore(s);
            if (sc > bestScore) { bestScore = sc; best = i; }
        }
        return best;
    }

    // ---- elytra durability (for the e-bounce elytra refill) ----
    private int remainingDurability(@Nullable ItemStack s) {
        if (s == null || s == Container.EMPTY_STACK) return 0;
        var data = ItemRegistry.REGISTRY.get(s.getId());
        if (data == null) return 0;
        Integer maxDamage = data.components().get(DataComponentTypes.MAX_DAMAGE);
        if (maxDamage == null) return Integer.MAX_VALUE;
        Integer damage = s.getDataComponentsOrEmpty().get(DataComponentTypes.DAMAGE);
        return maxDamage - (damage == null ? 0 : damage);
    }
    /** A usable spare: an elytra with more durability than the fresh floor (shared with ElytraPilot's swap logic). */
    private boolean isFreshElytra(@Nullable ItemStack s) {
        return FlightGear.isElytra(s) && remainingDurability(s) > CONFIG.client.extra.elytraPilot.freshElytraMinDurability;
    }
    /** A worn-out elytra to dump back into the kit (an elytra at/below the fresh floor). */
    private boolean isSpentElytra(@Nullable ItemStack s) {
        return FlightGear.isElytra(s) && remainingDurability(s) <= CONFIG.client.extra.elytraPilot.freshElytraMinDurability;
    }
    /** An XP bottle — sourced for ElytraPilot's post-resupply Mending repair, never opened/used by Regear itself. */
    private boolean isXpBottle(@Nullable ItemStack s) { return matchesName(s, "experience_bottle"); }
    /** Count of elytras inside a shulker (its CONTAINER component) — for matchByElytraCount kit identification. */
    private int countElytrasIn(@Nullable ItemStack shulker) {
        int n = 0;
        for (ItemStack inner : containerContents(shulker)) if (FlightGear.isElytra(inner)) n++;
        return n;
    }

    // ---------------------------------------------------------------- cherry-pick fallback

    /** Would pulling {@code s} help meet a still-unmet deficit under the CURRENT resupply mode? Drives both the
     *  cherry-pick candidate scan ({@link #findRichestShulkerSlot}, a read-only peek into echest shulkers) and,
     *  for flight-refill / cherry-pick rounds, the extraction filter in {@link #tickEmptyKit}.
     *  <p>elytraRefill (e-bounce) deliberately stays narrow: elytras (freshness-aware, via {@link #isFreshElytra})
     *  plus food/totems (count-only, via {@link FlightGear#stillNeeds}) — the things that actually deplete over a
     *  long bounce trip. Armor/pickaxe/fireworks/echest don't, and e-bounce itself is firework-free, so those are
     *  deliberately left out of scope here even though {@code FlightGear.stillNeeds} could report them short. */
    private boolean cherryPickStillNeeds(@Nullable ItemStack s) {
        if (s == null || s == Container.EMPTY_STACK) return false;
        if (flightRefill) return FlightGear.stillNeeds(s);
        if (elytraRefill) {
            if (FlightGear.isElytra(s)) return countInInv(this::isFreshElytra) < elytraRefillTarget;
            if (isXpBottle(s)) return mendBottleTarget > 0 && countInInv(this::isXpBottle) < mendBottleTarget;
            return (FlightGear.isEgap(s) || FlightGear.isTotem(s)) && FlightGear.stillNeeds(s);
        }
        return regearStillNeeds(s);
    }

    /** Have all deficits under the current resupply mode been met - i.e. is it still worth opening another shulker? */
    private boolean cherryPickSatisfied() {
        if (flightRefill) return !FlightGear.anyDeficit();
        if (elytraRefill) return countInInv(this::isFreshElytra) >= elytraRefillTarget
            && (mendBottleTarget <= 0 || countInInv(this::isXpBottle) >= mendBottleTarget)
            && FlightGear.egapCountSatisfied() && FlightGear.totemCountSatisfied();
        return regearSatisfied();
    }

    /** Normal (non-flight) regear's per-item deficit check: does {@code s} fill an armour/elytra/totem slot
     *  that's still empty AND not already covered by something sitting loose in the inventory? Mirrors
     *  {@link #tickGearUp}'s fill logic, but checks "anywhere" (worn OR carried) since gear-up hasn't run yet
     *  when this is consulted mid-cycle. */
    private boolean regearStillNeeds(ItemStack s) {
        var cfg = CONFIG.client.extra.regear;
        String n = itemName(s);
        if (n == null) return false;
        if (cfg.equipElytra && n.equals("elytra")) {
            return !wornIsElytra() && findInInv(s2 -> "elytra".equals(itemName(s2))) == -1;
        }
        if (cfg.equipArmor) {
            for (int i = 0; i < ARMOR_SUFFIX.length; i++) {
                if (i == 1 && cfg.equipElytra) continue;   // chest slot handled above when flying
                if (!n.endsWith(ARMOR_SUFFIX[i])) continue;
                if (CACHE.getPlayerCache().getEquipment(ARMOR_EQUIP[i]) != Container.EMPTY_STACK) return false;
                final int idx = i;
                return findInInv(s2 -> { String n2 = itemName(s2); return n2 != null && n2.endsWith(ARMOR_SUFFIX[idx]); }) == -1;
            }
        }
        if (cfg.offhandTotem && n.equals("totem_of_undying")) {
            return CACHE.getPlayerCache().getEquipment(EquipmentSlot.OFF_HAND) == Container.EMPTY_STACK
                && findInInv(s2 -> matchesName(s2, "totem_of_undying")) == -1;
        }
        return false;
    }

    /** Normal (non-flight) regear's "have we got everything the config asks for, anywhere (worn or carried)" check. */
    private boolean regearSatisfied() {
        var cfg = CONFIG.client.extra.regear;
        if (cfg.equipElytra && !wornIsElytra() && findInInv(s -> "elytra".equals(itemName(s))) == -1) return false;
        if (cfg.equipArmor) {
            for (int i = 0; i < ARMOR_SUFFIX.length; i++) {
                if (i == 1 && cfg.equipElytra) continue;
                if (CACHE.getPlayerCache().getEquipment(ARMOR_EQUIP[i]) != Container.EMPTY_STACK) continue;
                final int idx = i;
                if (findInInv(s -> { String n = itemName(s); return n != null && n.endsWith(ARMOR_SUFFIX[idx]); }) == -1) return false;
            }
        }
        if (cfg.offhandTotem && CACHE.getPlayerCache().getEquipment(EquipmentSlot.OFF_HAND) == Container.EMPTY_STACK
            && findInInv(s -> matchesName(s, "totem_of_undying")) == -1) return false;
        return true;
    }

    private boolean wornIsElytra() {
        var worn = CACHE.getPlayerCache().getEquipment(EquipmentSlot.CHESTPLATE);
        return worn != Container.EMPTY_STACK && "elytra".equals(itemName(worn));
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
