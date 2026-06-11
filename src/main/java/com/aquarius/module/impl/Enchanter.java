package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.cache.data.inventory.Container;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.enchant.EnchantCost;
import com.aquarius.feature.enchant.EnchantCost.EnchItem;
import com.aquarius.feature.enchant.EnchantOptimizer;
import com.aquarius.feature.enchant.EnchantTemplates;
import com.aquarius.feature.pathfinder.goals.GoalNear;
import com.aquarius.feature.player.ClickTarget;
import com.aquarius.feature.player.Input;
import com.aquarius.feature.player.InputRequest;
import com.aquarius.mc.block.BlockPos;
import com.aquarius.mc.enchantment.EnchantmentData;
import com.aquarius.mc.enchantment.EnchantmentRegistry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemEnchantments;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.BARITONE;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.INPUTS;

/**
 * Enchanter — auto-builds max-template gear in a purpose-built anvil station.
 *
 * <p>The bot auto-discovers the station within {@link Config.Client.Extra.Enchanter#scanRadius}
 * blocks (an anvil — the bottom of a gravity-fed anvil pillar — plus chests/barrels it classifies as
 * the un-enchanted-gear input, the enchanted-book source(s), and the finished-gear output), then
 * loops: pull a base item, resolve its built-in template (e.g. <i>max Sharpness sword</i> with all
 * other compatible enchants), compute the <b>cheapest anvil combine order</b> with
 * {@link EnchantOptimizer}, gather the needed books, run the anvil operations in that order, and
 * deposit the finished item.
 *
 * <p>The optimizer keeps every single anvil op under the survival "Too Expensive!" cap and minimizes
 * total XP by merging books into intermediates before applying them to the gear (the prior-work
 * penalty doubles each time the gear itself is used). The bot can't read its XP level or the anvil's
 * cost from the cache, so it detects a blocked/under-funded combine by reading the anvil output slot
 * (slot 2) and confirming the result actually reached its inventory.
 *
 * <p>When the working anvil shatters, the next anvil in the pillar falls into its place; the bot
 * detects the gap, waits for the replacement to land and settle, then continues. Pillar exhausted
 * (or any unexpected state) -> soft pause + operator alert, never a hang. Modelled on
 * {@link KitMaker}; reuses the container/pathing/inventory primitives in {@link AbstractFieldModule}.
 */
public class Enchanter extends AbstractFieldModule {

    private enum State {
        IDLE, SCAN_LAYOUT, NEXT_ITEM, PLAN, GATHER, PULL_BOTTLES, COMBINE, DEPOSIT, DONE
    }

    /** One enchanted book the plan still needs to pull from the book chests. */
    private record Need(String ench, int level) {}

    private State state = State.IDLE;
    private int step;
    private int timer;
    private int attempts;
    private @Nullable GoalNear pathGoal;

    // discovered station
    private @Nullable BlockPos anvilPos;
    private @Nullable BlockPos inputChest;
    private final List<BlockPos> bookChests = new ArrayList<>();
    private @Nullable BlockPos outputChest;
    private @Nullable BlockPos xpChest;
    private final List<BlockPos> scanQueue = new ArrayList<>();

    // xp-bottle charging
    private State afterBottles = State.COMBINE;   // where PULL_BOTTLES returns to
    private int bottleTarget;                      // pull bottles until carrying at least this many
    private int throwState;                        // throw cycle: 0 = press (throw), 1 = confirm + release
    private int bottlesBeforeThrow;                // carried-bottle count snapshot, to confirm a throw landed
    private int throwTries;

    // current item being built
    private @Nullable String currentItemName;
    private final List<Need> neededBooks = new ArrayList<>();
    private EnchantOptimizer.@Nullable Plan plan;
    private int stepIdx;        // which combine step
    private int bookChestIdx;   // which book chest during GATHER

    // anvil pillar settle tracking
    private int anvilStableTicks;
    private int pillarWaitTicks;

    private int cyclesDone;
    private String doneReason = "stopped";
    private boolean paused;
    private boolean complete;
    private boolean hazardPaused;
    private volatile boolean rescanRequested;

    @Override
    public boolean enabledSetting() { return CONFIG.client.extra.enchanter.enabled; }

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
        cyclesDone = 0;
        anvilPos = null; inputChest = null; outputChest = null; xpChest = null;
        bookChests.clear(); scanQueue.clear();
        throwState = 0;
        resetItem();
        setBreakingAllowed(false);     // never dig in the station
        go(State.SCAN_LAYOUT);
        info("Enchanter: scanning the station (anvil + chests) within {} blocks.", CONFIG.client.extra.enchanter.scanRadius);
    }

    @Override
    public void onDisable() {
        if (BARITONE.isActive()) BARITONE.stop();
        restoreBreaking();
        state = State.IDLE;
    }

    private void onStarting(ClientBotTick.Starting event) {
        if (state != State.IDLE && !complete) onEnable();   // re-scan cleanly after a (re)connect
    }

    private void resetItem() {
        currentItemName = null;
        neededBooks.clear();
        plan = null;
        stepIdx = 0;
        bookChestIdx = 0;
    }

    private void go(State s) { state = s; step = 0; timer = 0; attempts = 0; }

    private void stop(String reason) {
        if (BARITONE.isActive()) BARITONE.stop();
        restoreBreaking();
        complete = true;
        state = State.IDLE;
        info("Enchanter: {} ({} items enchanted).", reason, cyclesDone);
        inGameAlertActivePlayer("<green>Enchanter done: " + reason + " (" + cyclesDone + " items)");
        CONFIG.client.extra.enchanter.enabled = false;
        syncEnabledFromConfig();
        if (CONFIG.client.extra.enchanter.autoDisconnect) executeCommand("disconnect", true);
    }

    private void abort(String reason) {
        if (BARITONE.isActive()) BARITONE.stop();
        restoreBreaking();
        paused = true;
        state = State.IDLE;
        warn("Enchanter paused: {}. Fix it and toggle /enc off/on.", reason);
        inGameAlertActivePlayer("<red>Enchanter paused: " + reason);
    }

    // ---------------------------------------------------------------- tick

    /** Ask the tick thread to re-discover the station from a clean state (consumed at the top of onTick). */
    public void requestRescan() { rescanRequested = true; }

    private void onTick(ClientBotTick event) {
        var cfg = CONFIG.client.extra.enchanter;
        if (notReady()) return;
        if (rescanRequested) { rescanRequested = false; onEnable(); return; }
        if (state == State.IDLE || complete || paused) return;

        if (cfg.pauseOnPlayer && playerNearby(cfg.playerPauseRange)) {
            if (!hazardPaused) {
                hazardPaused = true;
                if (BARITONE.isActive()) BARITONE.stop();
                warn("Enchanter: player within {} blocks - pausing.", (int) cfg.playerPauseRange);
            }
            return;
        }
        if (hazardPaused) { hazardPaused = false; info("Enchanter: clear - resuming."); }

        if (timer > 0) { timer--; return; }

        switch (state) {
            case SCAN_LAYOUT -> tickScanLayout();
            case NEXT_ITEM -> tickNextItem();
            case PLAN -> tickPlan();
            case GATHER -> tickGather();
            case PULL_BOTTLES -> tickPullBottles();
            case COMBINE -> tickCombine();
            case DEPOSIT -> tickDeposit();
            case DONE -> stop(doneReason);
            default -> { }
        }
    }

    // ---------------------------------------------------------------- scan / classify

    private void tickScanLayout() {
        var cfg = CONFIG.client.extra.enchanter;
        switch (step) {
            case 0 -> {
                BlockPos pf = playerFeet();
                int radius = Math.min(32, cfg.scanRadius);
                anvilPos = nearestBlock(radius, pf.y() - cfg.bandDown, pf.y() + cfg.bandUp, Enchanter::isAnvilName);
                List<BlockPos> raw = findBlocks(radius, pf.y() - cfg.bandDown, pf.y() + cfg.bandUp, this::isContainerBlockName);
                scanQueue.clear();
                bookChests.clear(); inputChest = null; outputChest = null;
                for (BlockPos b : raw) {
                    if (isDoubleHalfAlreadyQueued(b)) continue;
                    scanQueue.add(b);
                }
                if (anvilPos == null) { abort("no anvil found within " + radius + " blocks"); return; }
                if (scanQueue.isEmpty()) { abort("no chests/barrels found within " + radius + " blocks"); return; }
                info("Enchanter: anvil @ {}, classifying {} containers.", anvilPos, scanQueue.size());
                step = 1;
            }
            case 1 -> {
                if (scanQueue.isEmpty()) { finishLayout(); return; }
                BlockPos cand = scanQueue.get(0);
                if (!arrivedAt(new GoalNear(cand, REACH_RANGE_SQ))) { pathGoal = pathToNear(cand); timer = cfg.actionDelayTicks; return; }
                if (BARITONE.isActive()) BARITONE.stop();
                open(cand); timer = cfg.settleTicks; step = 2; attempts = 0;
            }
            default -> {
                BlockPos cand = scanQueue.get(0);
                if (openContainerId() == 0) {
                    if (++attempts >= 6) { scanQueue.remove(0); step = 1; return; }    // skip unreachable
                    open(cand); timer = cfg.settleTicks; return;
                }
                Container c = openContainer();
                if (c != null) classify(cand, c);
                closeContainer();
                scanQueue.remove(0);
                step = 1; timer = cfg.actionDelayTicks;
            }
        }
    }

    /** Classify a container by its contents: XP-bottle source, book source, gear input, or finished output. */
    private void classify(BlockPos pos, Container c) {
        boolean anyBottle = false, anyBook = false, anyGear = false, anyItem = false;
        int chestSlots = Math.max(0, c.getSize() - 36);
        for (int i = 0; i < chestSlots; i++) {
            ItemStack s = c.getItemStack(i);
            if (s == Container.EMPTY_STACK) continue;
            anyItem = true;
            if (isBottle(s)) anyBottle = true;
            else if (isEnchantedBook(s)) anyBook = true;
            else if (isBaseGear(s)) anyGear = true;
        }
        if (anyBottle && xpChest == null) { xpChest = pos; info("Enchanter:  XP-bottle source @ {}", pos); return; }
        if (anyBook) { if (!bookChests.contains(pos)) bookChests.add(pos); info("Enchanter:  book source @ {}", pos); return; }
        if (anyGear && inputChest == null) { inputChest = pos; info("Enchanter:  gear input @ {}", pos); return; }
        if (!anyItem && outputChest == null) { outputChest = pos; info("Enchanter:  finished output @ {}", pos); }
    }

    private void finishLayout() {
        if (inputChest == null) { abort("no chest of un-enchanted gear found (the input)"); return; }
        if (bookChests.isEmpty()) { abort("no chest of enchanted books found (the book source)"); return; }
        if (outputChest == null) { abort("no empty chest found for finished gear (the output)"); return; }
        if (CONFIG.client.extra.enchanter.throwXp && xpChest == null) { abort("no XP-bottle chest found (or turn off /enc xp)"); return; }
        info("Enchanter: layout OK - {} book source(s){}. Enchanting.", bookChests.size(), xpChest != null ? ", XP chest" : "");
        go(State.NEXT_ITEM);
    }

    /** True if a horizontally-adjacent same-name container is already queued (dedupe double-chest halves). */
    private boolean isDoubleHalfAlreadyQueued(BlockPos b) {
        String name = com.aquarius.feature.player.World.getBlock(b.x(), b.y(), b.z()).name();
        for (BlockPos q : scanQueue) {
            if (q.y() != b.y()) continue;
            if ((Math.abs(q.x() - b.x()) == 1 && q.z() == b.z()) || (Math.abs(q.z() - b.z()) == 1 && q.x() == b.x())) {
                if (com.aquarius.feature.player.World.getBlock(q.x(), q.y(), q.z()).name().equals(name)) return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- next item

    private void tickNextItem() {
        var cfg = CONFIG.client.extra.enchanter;
        if (cfg.maxItems > 0 && cyclesDone >= cfg.maxItems) { doneReason = "reached max items (" + cfg.maxItems + ")"; go(State.DONE); return; }
        switch (step) {
            case 0 -> {
                // resume-safe: if a base item is already carried (interrupted cycle), use it
                int held = findInInv(this::isBaseGear);
                if (held != -1) { go(State.PLAN); return; }
                if (!arrivedAt(new GoalNear(inputChest, REACH_RANGE_SQ))) { pathGoal = pathToNear(inputChest); timer = cfg.actionDelayTicks; return; }
                if (BARITONE.isActive()) BARITONE.stop();
                open(inputChest); timer = cfg.settleTicks; step = 1; attempts = 0;
            }
            case 1 -> {
                if (openContainerId() == 0) {
                    if (++attempts >= 6) { abort("gear input chest wouldn't open"); return; }
                    open(inputChest); timer = cfg.settleTicks; return;
                }
                Container c = openContainer();
                int src = c == null ? -1 : findContainerSlot(c, this::isBaseGear);
                if (src == -1) { closeContainer(); doneReason = "no more gear in the input chest"; go(State.DONE); return; }
                if (!inventoryBusy()) shiftClick(c, src);
                timer = cfg.fillDelayTicks; step = 2;
            }
            default -> {
                Container c = openContainer();
                if (c != null && findPlayerWindowSlot(c, this::isBaseGear) != -1) { closeContainer(); go(State.PLAN); }
                else if (++attempts >= 12) { abort("pulled a gear item but it didn't reach the inventory"); }
                else timer = cfg.fillDelayTicks;
            }
        }
    }

    // ---------------------------------------------------------------- plan

    private void tickPlan() {
        int slot = findInInv(this::isBaseGear);
        if (slot == -1) { go(State.NEXT_ITEM); return; }       // gear vanished -> pull another
        ItemStack gear = CACHE.getPlayerCache().getPlayerInventory().get(slot);
        currentItemName = itemName(gear);
        Map<String, Integer> existing = enchMapOf(gear);
        int repairCost = repairCostOf(gear);

        Map<String, Integer> target = EnchantTemplates.forItem(currentItemName, CONFIG.client.extra.enchanter.variantChoices);
        if (target == null) { abort("no template for " + currentItemName); return; }

        neededBooks.clear();
        for (var e : target.entrySet()) {
            if (existing.getOrDefault(e.getKey(), 0) < e.getValue()) neededBooks.add(new Need(e.getKey(), e.getValue()));
        }
        if (neededBooks.isEmpty()) {            // already at template -> just deposit it
            info("Enchanter: {} already matches its template - depositing.", currentItemName);
            go(State.DEPOSIT); return;
        }

        EnchItem base = EnchItem.item(existing, repairCost);
        List<EnchItem> bookLeaves = new ArrayList<>();
        for (Need n : neededBooks) bookLeaves.add(EnchItem.book(n.ench(), n.level()));
        plan = EnchantOptimizer.optimize(base, bookLeaves);
        if (!plan.feasible) {
            abort("can't build " + currentItemName + " under the 40-level cap (" + neededBooks.size() + " enchants)");
            return;
        }
        stepIdx = 0; bookChestIdx = 0;
        info("Enchanter: {} -> {} books, {} anvil steps, ~{} levels total.",
            currentItemName, neededBooks.size(), plan.steps.size(), plan.totalCost);
        go(State.GATHER);
    }

    // ---------------------------------------------------------------- gather books

    private void tickGather() {
        var cfg = CONFIG.client.extra.enchanter;
        switch (step) {
            case 0 -> {
                if (allBooksGathered()) { if (openContainerId() != 0) closeContainer(); afterGather(); return; }
                if (bookChestIdx >= bookChests.size()) { if (openContainerId() != 0) closeContainer(); abort("missing books: " + missingBooksStr()); return; }
                BlockPos src = bookChests.get(bookChestIdx);
                if (!arrivedAt(new GoalNear(src, REACH_RANGE_SQ))) { pathGoal = pathToNear(src); timer = cfg.actionDelayTicks; return; }
                if (BARITONE.isActive()) BARITONE.stop();
                open(src); timer = cfg.settleTicks; step = 1; attempts = 0;
            }
            case 1 -> {
                if (openContainerId() == 0) {
                    if (++attempts >= 6) { bookChestIdx++; step = 0; return; }   // skip unreachable
                    open(bookChests.get(bookChestIdx)); timer = cfg.settleTicks; return;
                }
                step = 2;
            }
            default -> {
                if (openContainerId() == 0) { step = 0; return; }
                Container c = openContainer();
                if (c == null) { timer = cfg.fillDelayTicks; return; }
                int slot = neededBookContainerSlot(c);
                if (slot != -1) { if (!inventoryBusy()) shiftClick(c, slot); timer = cfg.fillDelayTicks; }
                else { closeContainer(); bookChestIdx++; step = 0; timer = cfg.actionDelayTicks; }
            }
        }
    }

    /** Container-half slot of a single-enchant book this plan still needs and doesn't yet hold, or -1. */
    private int neededBookContainerSlot(Container c) {
        int chestSlots = Math.max(0, c.getSize() - 36);
        for (int i = 0; i < chestSlots; i++) {
            ItemStack s = c.getItemStack(i);
            if (s == Container.EMPTY_STACK) continue;
            for (Need n : neededBooks) {
                if (!liveHasSingleBook(c, n) && isSingleBook(s, n)) return i;
            }
        }
        return -1;
    }

    private boolean allBooksGathered() {
        Container c = openContainerId() != 0 ? openContainer() : null;
        for (Need n : neededBooks) if (!liveHasSingleBook(c, n)) return false;
        return true;
    }

    private String missingBooksStr() {
        Container c = openContainerId() != 0 ? openContainer() : null;
        StringBuilder sb = new StringBuilder();
        for (Need n : neededBooks) {
            if (liveHasSingleBook(c, n)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(n.ench()).append(' ').append(n.level());
        }
        return sb.toString();
    }

    /** Do we carry a single-enchant book for {@code need}? Reads the OPEN window's player slots if a
     *  container is open ({@code c} != null; the standalone cache is stale then), else the inventory. */
    private boolean liveHasSingleBook(@Nullable Container c, Need need) {
        if (c != null) {
            int size = c.getSize();
            for (int i = size - 36; i < size; i++) if (isSingleBook(c.getItemStack(i), need)) return true;
            return false;
        }
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 9; i <= 44; i++) if (isSingleBook(inv.get(i), need)) return true;
        return false;
    }

    /** After books are gathered: top up the XP-bottle reserve (if charging) before combining. */
    private void afterGather() {
        var cfg = CONFIG.client.extra.enchanter;
        if (cfg.throwXp && xpChest != null) {
            int want = Math.max(cfg.xpBottleReserve, bottleEstimate());
            if (countBottlesInInv() < want) { bottleTarget = want; afterBottles = State.COMBINE; go(State.PULL_BOTTLES); return; }
        }
        go(State.COMBINE);
    }

    /** Pull XP bottles from the bottle chest until carrying {@link #bottleTarget}, then return to {@link #afterBottles}. */
    private void tickPullBottles() {
        var cfg = CONFIG.client.extra.enchanter;
        if (xpChest == null) { go(afterBottles); return; }
        switch (step) {
            case 0 -> {
                if (countBottlesInInv() >= bottleTarget) { go(afterBottles); return; }
                if (!arrivedAt(new GoalNear(xpChest, REACH_RANGE_SQ))) { pathGoal = pathToNear(xpChest); timer = cfg.actionDelayTicks; return; }
                if (BARITONE.isActive()) BARITONE.stop();
                open(xpChest); timer = cfg.settleTicks; step = 1; attempts = 0;
            }
            case 1 -> {
                if (openContainerId() == 0) {
                    if (++attempts >= 6) { abort("XP-bottle chest wouldn't open"); return; }
                    open(xpChest); timer = cfg.settleTicks; return;
                }
                step = 2;
            }
            default -> {
                if (openContainerId() == 0) { step = 0; return; }
                Container c = openContainer();
                if (c == null) { timer = cfg.fillDelayTicks; return; }
                if (countBottlesInInv() >= bottleTarget) { closeContainer(); go(afterBottles); return; }
                int slot = findContainerSlot(c, this::isBottle);
                if (slot == -1) {     // chest dry — use what we have (or fail if none at all)
                    closeContainer();
                    if (countBottlesInInv() <= 0) { abort("XP-bottle chest is empty - restock it"); return; }
                    go(afterBottles);
                    return;
                }
                if (!inventoryBusy()) shiftClick(c, slot);
                timer = cfg.fillDelayTicks;
            }
        }
    }

    /** A safe over-estimate of bottles a run consumes (XP points per step / a conservative 5 per bottle). */
    private int bottleEstimate() {
        if (plan == null) return 0;
        int points = 0;
        for (EnchantOptimizer.Step s : plan.steps) points += EnchantCost.xpForLevel(s.cost + CONFIG.client.extra.enchanter.xpBufferLevels);
        return (int) Math.ceil(points / 5.0);
    }

    // ---------------------------------------------------------------- combine in the anvil

    private void tickCombine() {
        var cfg = CONFIG.client.extra.enchanter;
        if (plan == null || stepIdx >= plan.steps.size()) { go(State.DEPOSIT); return; }
        EnchantOptimizer.Step s = plan.steps.get(stepIdx);

        switch (step) {
            case 0 -> {     // top up XP for THIS step, throwing bottles in place (stays low on the XP curve)
                if (!cfg.throwXp || curLevel() >= s.cost + cfg.xpBufferLevels) { step = 1; return; }
                if (BARITONE.isActive()) BARITONE.stop();
                if (countBottlesInInv() <= 0) {      // out of bottles -> refill from the chest, then resume this step
                    bottleTarget = Math.max(cfg.xpBottleReserve, bottleEstimate());
                    afterBottles = State.COMBINE;
                    go(State.PULL_BOTTLES);
                    return;
                }
                if (!isBottle(heldStack())) {        // hold a bottle first
                    int bslot = findInInv(this::isBottle);
                    if (bslot != -1) holdItemAt(bslot);
                    throwState = 0; timer = cfg.fillDelayTicks; return;
                }
                // throw ONE bottle, confirmed by the carried count dropping, then release + pace
                if (throwState == 0) {
                    bottlesBeforeThrow = countBottlesInInv();
                    submitThrow(cfg.xpThrowPitch);
                    throwState = 1; throwTries = 0; timer = 1;
                } else if (countBottlesInInv() < bottlesBeforeThrow) {
                    submitRelease(); throwState = 0; timer = cfg.xpThrowSpacingTicks;     // let the orb + level packet land
                } else if (++throwTries >= 10) {
                    submitRelease(); throwState = 0; timer = cfg.fillDelayTicks;           // didn't register - re-arm
                } else {
                    submitThrow(cfg.xpThrowPitch); timer = 1;                              // keep pressing until it throws
                }
            }
            case 1 -> {     // ensure the anvil is present + settled, then go to it and open
                if (anvilPos == null) { abort("lost the anvil position"); return; }
                if (!anvilPresent()) {
                    anvilStableTicks = 0;
                    if (++pillarWaitTicks > cfg.pillarTimeoutTicks) { abort("anvil pillar exhausted (no anvil fell into place)"); return; }
                    if (BARITONE.isActive()) BARITONE.stop();
                    timer = 2; return;
                }
                if (++anvilStableTicks < cfg.anvilSettleTicks) { timer = 2; return; }   // let a freshly-fallen anvil settle
                pillarWaitTicks = 0;
                if (!arrivedAt(new GoalNear(anvilPos, REACH_RANGE_SQ))) { pathGoal = pathToNear(anvilPos); timer = cfg.actionDelayTicks; return; }
                if (BARITONE.isActive()) BARITONE.stop();
                open(anvilPos); timer = cfg.settleTicks; step = 2; attempts = 0;
            }
            case 2 -> {     // wait for the anvil window
                Container c = openContainer();
                if (openContainerId() == 0 || c == null || c.getType() != ContainerType.ANVIL) {
                    if (!anvilPresent()) { step = 1; return; }       // it shattered while opening
                    if (++attempts >= 6) { abort("anvil wouldn't open"); return; }
                    open(anvilPos); timer = cfg.settleTicks; return;
                }
                step = 3;
            }
            case 3 -> {     // place the target into input slot 0
                Container c = openContainer();
                if (c == null || openContainerId() == 0) { step = 1; return; }
                if (matchesSig(c.getItemStack(0), s.target)) { step = 4; return; }
                int tslot = findPlayerWindowSlot(c, w -> matchesSig(w, s.target));
                if (tslot == -1) { abort("lost the target item for anvil step " + (stepIdx + 1)); return; }
                if (!inventoryBusy()) shiftClick(c, tslot);
                timer = cfg.fillDelayTicks;
            }
            case 4 -> {     // place the sacrifice into input slot 1
                Container c = openContainer();
                if (c == null || openContainerId() == 0) { step = 1; return; }
                if (matchesSig(c.getItemStack(1), s.sacrifice)) { timer = cfg.anvilSettleTicks; step = 5; attempts = 0; return; }
                int sslot = findPlayerWindowSlot(c, w -> matchesSig(w, s.sacrifice));
                if (sslot == -1) { abort("lost the sacrifice item for anvil step " + (stepIdx + 1)); return; }
                if (!inventoryBusy()) shiftClick(c, sslot);
                timer = cfg.fillDelayTicks;
            }
            case 5 -> {     // read + take the result from output slot 2
                Container c = openContainer();
                if (c == null || openContainerId() == 0) { step = 1; return; }
                ItemStack out = c.getItemStack(2);
                if (out == Container.EMPTY_STACK) {
                    if (++attempts >= 8) { abort("anvil output empty on step " + (stepIdx + 1) + " (too expensive / items mismatch)"); return; }
                    timer = cfg.anvilSettleTicks; return;
                }
                if (!inventoryBusy()) shiftClick(c, 2);
                timer = cfg.anvilSettleTicks; step = 6; attempts = 0;
            }
            default -> {    // verify the result reached the inventory, then advance to the next step
                if (carrying(s.result)) {
                    if (openContainerId() != 0) closeContainer();
                    stepIdx++;
                    step = 0; timer = cfg.actionDelayTicks;     // step 0 re-checks XP for the next step
                    return;
                }
                if (++attempts >= 12) { abort("couldn't take the anvil result on step " + (stepIdx + 1) + " (insufficient XP?)"); return; }
                timer = cfg.anvilSettleTicks;
            }
        }
    }

    private boolean anvilPresent() {
        return anvilPos != null && isAnvilName(com.aquarius.feature.player.World.getBlock(anvilPos.x(), anvilPos.y(), anvilPos.z()).name());
    }

    /** Is the result stack carried? Reads the open window's player slots if a container is open, else the inv. */
    private boolean carrying(EnchItem sig) {
        if (openContainerId() != 0) {
            Container c = openContainer();
            return c != null && findPlayerWindowSlot(c, w -> matchesSig(w, sig)) != -1;
        }
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 9; i <= 44; i++) if (matchesSig(inv.get(i), sig)) return true;
        return false;
    }

    // ---------------------------------------------------------------- xp bottles

    private int curLevel() { return CACHE.getPlayerCache().getThePlayer().getLevel(); }

    private boolean isBottle(@Nullable ItemStack s) { return matchesName(s, "experience_bottle"); }

    private int countBottlesInInv() {
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        int n = 0;
        for (int i = 9; i <= 44; i++) { ItemStack st = inv.get(i); if (isBottle(st)) n += st.getAmount(); }
        return n;
    }

    private ItemStack heldStack() {
        var pc = CACHE.getPlayerCache();
        return pc.getPlayerInventory().get(36 + pc.getHeldItemSlot());
    }

    /** Use the held XP bottle (a single right-click "throw"), looking down so it breaks at the bot's feet. */
    private void submitThrow(float pitch) {
        INPUTS.submit(InputRequest.builder()
            .owner(this)
            .input(Input.builder()
                .pressingForward(false).pressingBack(false).sneaking(false).sprinting(false)
                .rightClick(true).hand(Hand.MAIN_HAND)
                .clickTarget(ClickTarget.None.INSTANCE).clickRequiresRotation(false)
                .build())
            .yaw(CACHE.getPlayerCache().getYaw())
            .pitch(pitch)
            .priority(ACTION_PRIORITY)
            .build());
    }

    /** Release the right-click between throws so the next press registers as a fresh use. */
    private void submitRelease() {
        var pc = CACHE.getPlayerCache();
        INPUTS.submit(InputRequest.builder()
            .owner(this)
            .input(Input.builder()
                .pressingForward(false).pressingBack(false).sneaking(false).sprinting(false)
                .rightClick(false).hand(Hand.MAIN_HAND)
                .clickTarget(ClickTarget.None.INSTANCE).clickRequiresRotation(false)
                .build())
            .yaw(pc.getYaw())
            .pitch(pc.getPitch())
            .priority(ACTION_PRIORITY)
            .build());
    }

    // ---------------------------------------------------------------- deposit

    private void tickDeposit() {
        var cfg = CONFIG.client.extra.enchanter;
        switch (step) {
            case 0 -> {
                if (!arrivedAt(new GoalNear(outputChest, REACH_RANGE_SQ))) { pathGoal = pathToNear(outputChest); timer = cfg.actionDelayTicks; return; }
                if (BARITONE.isActive()) BARITONE.stop();
                open(outputChest); timer = cfg.settleTicks; step = 1; attempts = 0;
            }
            case 1 -> {
                if (openContainerId() != 0) { step = 2; attempts = 0; }
                else if (++attempts >= 6) abort("output chest wouldn't open");
                else { open(outputChest); timer = cfg.settleTicks; }
            }
            default -> {
                Container c = openContainer();
                if (c == null) { step = 0; return; }
                int src = findPlayerWindowSlot(c, this::isFinishedGear);
                if (src == -1) {                         // gear deposited -> next item
                    closeContainer();
                    cyclesDone++;
                    info("Enchanter: item #{} ({}) finished + deposited.", cyclesDone, currentItemName);
                    resetItem();
                    go(State.NEXT_ITEM);
                    return;
                }
                if (outputFull(c)) { closeContainer(); doneReason = "output chest is full"; go(State.DONE); return; }
                if (!inventoryBusy()) shiftClick(c, src);
                timer = cfg.fillDelayTicks;
            }
        }
    }

    private boolean outputFull(Container c) {
        int chestSlots = Math.max(0, c.getSize() - 36);
        for (int i = 0; i < chestSlots; i++) if (c.getItemStack(i) == Container.EMPTY_STACK) return false;
        return true;
    }

    /** The finished gear = the carried piece of the current item type that is NOT an enchanted book. */
    private boolean isFinishedGear(@Nullable ItemStack s) {
        return s != null && s != Container.EMPTY_STACK && !isEnchantedBook(s) && matchesName(s, currentItemName);
    }

    // ---------------------------------------------------------------- item / enchant helpers

    private static boolean isAnvilName(String n) { return n.endsWith("anvil"); }   // anvil / chipped_anvil / damaged_anvil

    private boolean isEnchantedBook(@Nullable ItemStack s) { return matchesName(s, "enchanted_book"); }

    /** A container item we'd treat as raw input gear: has a known template family and isn't a book. */
    private boolean isBaseGear(@Nullable ItemStack s) {
        if (s == null || s == Container.EMPTY_STACK || isEnchantedBook(s)) return false;
        return EnchantTemplates.familyOf(itemName(s)) != null;
    }

    /** A single-enchant book carrying {@code need} at >= its level. */
    private boolean isSingleBook(@Nullable ItemStack s, Need need) {
        if (!isEnchantedBook(s)) return false;
        Map<String, Integer> m = enchMapOf(s);
        Integer lv = m.get(need.ench());
        return m.size() == 1 && lv != null && lv >= need.level();
    }

    /** A stack matches a plan signature: same book-ness, same enchant set (and item type, for gear). */
    private boolean matchesSig(@Nullable ItemStack s, EnchItem sig) {
        if (s == null || s == Container.EMPTY_STACK) return false;
        boolean book = isEnchantedBook(s);
        if (book != sig.isBook) return false;
        if (!book && !matchesName(s, currentItemName)) return false;   // pin gear to the worked item type
        return enchMapOf(s).equals(sig.enchants);
    }

    /** The enchant map (registry name -> level) of a stack: STORED_ENCHANTMENTS for books, else ENCHANTMENTS. */
    private Map<String, Integer> enchMapOf(@Nullable ItemStack s) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (s == null || s == Container.EMPTY_STACK) return out;
        var comps = s.getDataComponentsOrEmpty();
        ItemEnchantments e = isEnchantedBook(s) ? comps.get(DataComponentTypes.STORED_ENCHANTMENTS) : comps.get(DataComponentTypes.ENCHANTMENTS);
        if (e == null) e = comps.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (e == null) e = comps.get(DataComponentTypes.ENCHANTMENTS);
        if (e == null) return out;
        for (var entry : e.getEnchantments().int2IntEntrySet()) {
            EnchantmentData data = EnchantmentRegistry.REGISTRY.get(entry.getIntKey());
            if (data != null) out.put(data.name(), entry.getIntValue());
        }
        return out;
    }

    /** The prior-work penalty (minecraft:repair_cost) of a stack — 0 for a fresh item. */
    private int repairCostOf(@Nullable ItemStack s) {
        if (s == null || s == Container.EMPTY_STACK) return 0;
        Integer rc = s.getDataComponentsOrEmpty().get(DataComponentTypes.REPAIR_COST);
        return rc == null ? 0 : rc;
    }

    // ---------------------------------------------------------------- status

    public String statusLine() {
        if (complete) return "done (" + cyclesDone + " items)";
        if (paused) return "paused";
        if (state == State.IDLE) return "idle";
        String item = currentItemName == null ? "" : " " + currentItemName;
        String prog = (plan != null && state == State.COMBINE) ? " step " + (stepIdx + 1) + "/" + plan.steps.size() : "";
        return state.name().toLowerCase() + item + prog + " (" + cyclesDone + " done)";
    }

    public String setupLine() {
        return "anvil " + (anvilPos != null) + ", input " + (inputChest != null)
            + ", books " + bookChests.size() + ", output " + (outputChest != null)
            + ", xp-chest " + (xpChest != null);
    }

    /** Current XP level + carried bottles, for the command embed. */
    public String xpLine() {
        return "level " + curLevel() + ", bottles carried " + countBottlesInInv();
    }

    public boolean isPaused() { return paused; }
    public boolean isComplete() { return complete; }
}
