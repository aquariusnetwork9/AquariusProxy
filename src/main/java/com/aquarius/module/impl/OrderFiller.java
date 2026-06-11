package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.cache.data.inventory.Container;
import com.aquarius.database.StashDatabase;
import com.aquarius.database.StashQueue;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.pathfinder.goals.GoalNear;
import com.aquarius.feature.stash.StashModel.ChestRow;
import com.aquarius.feature.stash.StashModel.ReservedShulker;
import com.aquarius.mc.block.BlockPos;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.BARITONE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.DATABASE;

/**
 * OrderFiller — payment-gated order picker. Pops one paid job at a time from {@code orders:queue}, routes to
 * the order's reserved shulkers (grouped by chest, nearest-first), withdraws them with the ShiftClick fix,
 * deposits each batch into the order's assigned outgoing chest, then writes a manifest and decrements stock.
 * Movement runs with allowBreak=false / allowPlace=false; an unreachable chest or a missing shulker is logged
 * as a discrepancy and surfaced on the manifest as a shortfall — the bot never breaks, places, or carries a
 * shulker it can't deposit. Withdraw mirrors {@link Regear}; deposit mirrors {@link KitMaker}. See {@code .of}.
 */
public class OrderFiller extends AbstractFieldModule {

    private enum State { IDLE, ROUTE, REACH_CHEST, WITHDRAW, REACH_OUTGOING, DEPOSIT, FINISH }

    private State state = State.IDLE;
    private int step, timer, attempts, reachTicks;
    private boolean paused, hazardPaused;

    private int orderId = -1;
    private @Nullable ChestRow outgoing;
    private final List<ReservedShulker> remaining = new ArrayList<>();
    private final Map<Integer, Integer> filled = new HashMap<>();   // kitTypeId -> delivered count
    private int unreachableChests;

    // per-trip / per-chest runtime
    private int currentChestId = -1;
    private @Nullable BlockPos currentChestPos;
    private int carrying;
    private int prevWindowShulkers;
    private @Nullable ReservedShulker pendingPull;
    private int pendingTries;

    @Override
    public boolean enabledSetting() { return CONFIG.client.extra.orderFiller.enabled; }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(ClientBotTick.Starting.class, this::onStarting)
        );
    }

    private @Nullable StashDatabase db() { return DATABASE.getStashDatabase(); }
    private @Nullable StashQueue queue() { return DATABASE.getStashQueue(); }

    @Override
    public void onEnable() {
        paused = false; hazardPaused = false;
        resetJob();
        if (db() == null || queue() == null) { abort("stash database/queue not enabled (database.enabled + database.stashEnabled)"); return; }
        setBreakingAllowed(false);
        setPlacingAllowed(false);
        state = State.IDLE;
        info("OrderFiller: idle, waiting for paid jobs (break/place disabled).");
    }

    @Override
    public void onDisable() {
        if (BARITONE.isActive()) BARITONE.stop();
        restoreBreaking();
        restorePlacing();
        state = State.IDLE;
    }

    private void onStarting(ClientBotTick.Starting event) {
        // (re)connected: if we were mid-fill, fail the in-flight order safely (its reservations are released).
        if (orderId != -1) failOrder("disconnected mid-fill");
    }

    private void resetJob() {
        orderId = -1; outgoing = null; remaining.clear(); filled.clear(); unreachableChests = 0;
        currentChestId = -1; currentChestPos = null; carrying = 0; prevWindowShulkers = 0; pendingPull = null; pendingTries = 0;
        step = 0; timer = 0; attempts = 0; reachTicks = 0;
    }

    private void abort(String reason) {
        if (BARITONE.isActive()) BARITONE.stop();
        restoreBreaking(); restorePlacing();
        paused = true; state = State.IDLE;
        warn("OrderFiller paused: {}.", reason);
        inGameAlertActivePlayer("<red>OrderFiller paused: " + reason);
    }

    public void pause() { paused = true; if (BARITONE.isActive()) BARITONE.stop(); }
    public void resume() { paused = false; }

    // ---------------------------------------------------------------- tick

    private void onTick(ClientBotTick event) {
        var cfg = CONFIG.client.extra.orderFiller;
        if (notReady()) return;
        if (paused) return;

        if (cfg.pauseOnPlayer && playerNearby(cfg.playerPauseRange)) {
            if (!hazardPaused) { hazardPaused = true; if (BARITONE.isActive()) BARITONE.stop(); warn("OrderFiller: player nearby - pausing withdraw."); }
            return;
        }
        if (hazardPaused) { hazardPaused = false; info("OrderFiller: clear - resuming."); }

        if (timer > 0) { timer--; return; }

        switch (state) {
            case IDLE -> tickIdle();
            case ROUTE -> tickRoute();
            case REACH_CHEST -> tickReachChest();
            case WITHDRAW -> tickWithdraw();
            case REACH_OUTGOING -> tickReachOutgoing();
            case DEPOSIT -> tickDeposit();
            case FINISH -> finishOrder();
            default -> { }
        }
    }

    /** Poll the queue for the next paid order. */
    private void tickIdle() {
        StashQueue q = queue();
        if (q == null) { timer = 20; return; }
        Integer id = q.pollOrder();
        if (id == null) { timer = 20; return; }   // ~1s between polls
        beginOrder(id);
    }

    public void test(int id) { if (!paused) beginOrder(id); }   // manual dispatch (.of test <id>)

    private void beginOrder(int id) {
        StashDatabase db = db();
        if (db == null) { abort("stash database not enabled"); return; }
        resetJob();
        orderId = id;
        db.setOrderStatus(id, "filling");
        publishStatus("filling", null);
        remaining.addAll(db.loadReservedShulkers(id));
        if (remaining.isEmpty()) { info("OrderFiller: order {} has no reserved shulkers.", id); go(State.FINISH); return; }
        outgoing = db.assignOutgoingChest(id);
        if (outgoing == null) { failOrder("no free outgoing chest available"); return; }
        info("OrderFiller: filling order {} — {} shulkers -> outgoing chest {}.", id, remaining.size(), outgoing.label() != null ? outgoing.label() : outgoing.id());
        db.logFulfillment(id, "route", null, null, remaining.size(), null);
        go(State.ROUTE);
    }

    private void go(State s) { state = s; step = 0; timer = 0; attempts = 0; reachTicks = 0; }

    private void tickRoute() {
        if (remaining.isEmpty()) {
            if (carrying > 0) { go(State.REACH_OUTGOING); return; }
            go(State.FINISH); return;
        }
        // pick the next chest group (correctness over optimality: first remaining shulker's chest)
        ReservedShulker r = remaining.get(0);
        currentChestId = r.chestId();
        currentChestPos = new BlockPos(r.x(), r.y(), r.z());
        go(State.REACH_CHEST);
    }

    private void tickReachChest() {
        var cfg = CONFIG.client.extra.orderFiller;
        BlockPos pos = currentChestPos;
        if (pos == null) { go(State.ROUTE); return; }
        if (arrivedAt(new GoalNear(pos, REACH_RANGE_SQ))) {
            if (BARITONE.isActive()) BARITONE.stop();
            open(pos); timer = cfg.settleTicks; go(State.WITHDRAW); return;
        }
        if (reachTicks == 0 || !BARITONE.isActive()) pathToNear(pos);
        if (++reachTicks > cfg.reachTimeoutTicks) { dropChestGroup("unreachable"); go(State.ROUTE); }
        else timer = cfg.actionDelayTicks;
    }

    private void tickWithdraw() {
        var cfg = CONFIG.client.extra.orderFiller;
        StashDatabase db = db();
        if (db == null) { abort("stash database not enabled"); return; }
        if (openContainerId() == 0) {
            if (++attempts >= 6) { dropChestGroup("wouldn't open"); go(State.ROUTE); return; }
            if (currentChestPos != null) open(currentChestPos);
            timer = cfg.settleTicks; return;
        }
        Container c = openContainer();
        if (c == null) { timer = cfg.fillDelayTicks; return; }

        // confirm a previous pull landed in the inventory
        if (pendingPull != null) {
            int now = countWindowShulkers(c);
            if (now > prevWindowShulkers) {
                db.markWithdrawn(pendingPull.id());
                if (pendingPull.kitTypeId() != null) filled.merge(pendingPull.kitTypeId(), 1, Integer::sum);
                db.logFulfillment(orderId, "withdraw", currentChestId, pendingPull.kitTypeId(), 1, null);
                carrying++; remaining.remove(pendingPull); pendingPull = null; pendingTries = 0; prevWindowShulkers = now;
            } else if (++pendingTries > 12) {
                db.logFulfillment(orderId, "discrepancy", currentChestId, pendingPull.kitTypeId(), null, null);
                remaining.remove(pendingPull); pendingPull = null; pendingTries = 0;
            } else { timer = cfg.fillDelayTicks; return; }
        }

        if (carrying >= cfg.maxShulkersPerTrip) { closeContainer(); go(State.REACH_OUTGOING); return; }

        ReservedShulker r = nextInChest(currentChestId);
        if (r == null) { closeContainer(); go(State.ROUTE); return; }   // this chest done

        int slot = locateSource(c, r);
        if (slot == -1) {                                              // stock drifted: this shulker is gone
            db.logFulfillment(orderId, "discrepancy", currentChestId, r.kitTypeId(), null, null);
            remaining.remove(r); return;
        }
        if (!inventoryBusy()) {
            prevWindowShulkers = countWindowShulkers(c);
            shiftClick(c, slot);
            pendingPull = r; pendingTries = 0; timer = cfg.fillDelayTicks;
        }
    }

    private void tickReachOutgoing() {
        var cfg = CONFIG.client.extra.orderFiller;
        if (outgoing == null) { go(State.FINISH); return; }
        BlockPos pos = new BlockPos(outgoing.x(), outgoing.y(), outgoing.z());
        if (arrivedAt(new GoalNear(pos, REACH_RANGE_SQ))) {
            if (BARITONE.isActive()) BARITONE.stop();
            open(pos); timer = cfg.settleTicks; go(State.DEPOSIT); return;
        }
        if (reachTicks == 0 || !BARITONE.isActive()) pathToNear(pos);
        if (++reachTicks > cfg.reachTimeoutTicks) { failOrder("couldn't reach the outgoing chest"); }
        else timer = cfg.actionDelayTicks;
    }

    private void tickDeposit() {
        var cfg = CONFIG.client.extra.orderFiller;
        if (openContainerId() == 0) {
            if (++attempts >= 6) { failOrder("outgoing chest wouldn't open"); return; }
            if (outgoing != null) open(new BlockPos(outgoing.x(), outgoing.y(), outgoing.z()));
            timer = cfg.settleTicks; return;
        }
        Container c = openContainer();
        if (c == null) { timer = cfg.fillDelayTicks; return; }
        int src = findPlayerWindowSlot(c, this::isShulkerBox);
        if (src == -1) {                                              // everything deposited this trip
            closeContainer(); carrying = 0;
            go(remaining.isEmpty() ? State.FINISH : State.ROUTE);
            return;
        }
        if (containerFull(c)) {                                       // outgoing can't accept more
            closeContainer();
            if (db() != null) db().logFulfillment(orderId, "deposit", outgoing != null ? outgoing.id() : null, null, carrying, "outgoing full");
            warn("OrderFiller: outgoing chest full — {} shulkers undelivered.", carrying);
            go(State.FINISH);
            return;
        }
        if (!inventoryBusy()) shiftClick(c, src);
        timer = cfg.fillDelayTicks;
    }

    private void finishOrder() {
        StashDatabase db = db();
        if (db == null) { abort("stash database not enabled"); return; }
        if (BARITONE.isActive()) BARITONE.stop();
        // commit delivered counts and finalise
        filled.forEach((kit, n) -> db.addFilled(orderId, kit, n));
        db.releaseRemaining(orderId);
        var rows = db.manifestRows(orderId);
        int shortTotal = rows.stream().mapToInt(r -> r.shortQty()).sum();
        String status = shortTotal == 0 ? "delivered" : "ready";   // partial still completes (manifest shows the gap)
        db.setOrderStatus(orderId, status);
        db.logFulfillment(orderId, "manifest", outgoing != null ? outgoing.id() : null, null, shortTotal, null);
        db.markManifestPosted(orderId);
        info("OrderFiller: order {} {} ({} short).", orderId, status, shortTotal);
        publishStatus(status, "short=" + shortTotal);
        publishStatus("manifest", null);   // OrderService posts the manifest embed on this terminal signal
        resetJob();
        state = State.IDLE;
    }

    private void failOrder(String reason) {
        StashDatabase db = db();
        if (db != null) {
            db.releaseRemaining(orderId);
            db.setOrderStatus(orderId, "failed");
            db.logFulfillment(orderId, "error", null, null, null, reason);
        }
        warn("OrderFiller: order {} failed — {}.", orderId, reason);
        publishStatus("failed", reason);
        if (BARITONE.isActive()) BARITONE.stop();
        resetJob();
        state = State.IDLE;
    }

    /** Drop the current chest's reserved shulkers as discrepancies (e.g. unreachable) and surface as shortfall. */
    private void dropChestGroup(String why) {
        if (BARITONE.isActive()) BARITONE.stop();
        StashDatabase db = db();
        List<ReservedShulker> group = new ArrayList<>();
        for (ReservedShulker r : remaining) if (r.chestId() == currentChestId) group.add(r);
        for (ReservedShulker r : group) {
            if (db != null) db.logFulfillment(orderId, "unreachable", currentChestId, r.kitTypeId(), null, why);
            remaining.remove(r);
        }
        unreachableChests++;
        warn("OrderFiller: chest {} {} — {} shulkers skipped.", currentChestId, why, group.size());
    }

    // ---------------------------------------------------------------- helpers

    private @Nullable ReservedShulker nextInChest(int chestId) {
        for (ReservedShulker r : remaining) if (r.chestId() == chestId) return r;
        return null;
    }

    /** Find the source slot for a reserved shulker: its recorded slot if it still holds a shulker, else any shulker. */
    private int locateSource(Container c, ReservedShulker r) {
        int chestSlots = Math.max(0, c.getSize() - 36);
        if (r.slot() >= 0 && r.slot() < chestSlots && isShulkerBox(c.getItemStack(r.slot()))) return r.slot();
        return findContainerSlot(c, this::isShulkerBox);
    }

    private int countWindowShulkers(Container c) {
        int size = c.getSize(), n = 0;
        for (int i = Math.max(0, size - 36); i < size; i++) if (isShulkerBox(c.getItemStack(i))) n++;
        return n;
    }

    private boolean containerFull(Container c) {
        int chestSlots = Math.max(0, c.getSize() - 36);
        for (int i = 0; i < chestSlots; i++) if (c.getItemStack(i) == Container.EMPTY_STACK) return false;
        return true;
    }

    private void publishStatus(String status, @Nullable String detail) {
        StashQueue q = queue();
        if (q == null) return;
        String d = detail == null ? "" : ",\"detail\":\"" + detail.replace("\"", "'") + "\"";
        q.publishStatus("{\"order_id\":" + orderId + ",\"status\":\"" + status + "\"" + d + "}");
    }

    public String statusLine() {
        if (paused) return "paused";
        if (orderId == -1) return "idle (waiting for jobs)";
        return state.name().toLowerCase() + " order " + orderId + " (" + remaining.size() + " left, " + carrying + " carried)";
    }
}
