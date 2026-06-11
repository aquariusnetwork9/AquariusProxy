package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.cache.data.inventory.Container;
import com.aquarius.database.StashDatabase;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.pathfinder.goals.GoalNear;
import com.aquarius.feature.player.World;
import com.aquarius.feature.stash.KitClassifier;
import com.aquarius.feature.stash.StashModel.ChestRow;
import com.aquarius.feature.stash.StashModel.Classification;
import com.aquarius.feature.stash.StashModel.ContentItem;
import com.aquarius.mc.block.BlockPos;
import com.aquarius.util.config.Config.Client.Extra.StashScanner.ScanMode;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.BARITONE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.DATABASE;

/**
 * StashScanner — read-only stash census. Walks a region (or a DB-listed set) of storage containers, opens
 * each, reads every shulker box's contents from its CONTAINER component (never placing a shulker to read
 * it), classifies it as a kit and writes a snapshot to Postgres. The whole walk runs with allowBreak=false
 * / allowPlace=false; a chest it can't reach (or open) is marked unreachable, logged and skipped — never
 * dug or pillared to. Distilled from {@link KitMaker}'s discovery/classify loop, but it records instead of
 * filling and harvests nothing. See {@code .ss}.
 */
public class StashScanner extends AbstractFieldModule {

    private enum State { IDLE, PLAN, SCAN, FINALIZE }

    private State state = State.IDLE;
    private int step, timer, attempts, reachTicks;
    private @Nullable GoalNear reachGoal;

    private final List<BlockPos> queue = new ArrayList<>();
    private int scanId = -1;
    private int scanned, unreached, shulkersFound;
    private boolean complete, paused, hazardPaused;

    @Override
    public boolean enabledSetting() { return CONFIG.client.extra.stashScanner.enabled; }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(ClientBotTick.Starting.class, this::onStarting)
        );
    }

    private @Nullable StashDatabase db() { return DATABASE.getStashDatabase(); }

    @Override
    public void onEnable() {
        complete = false; paused = false; hazardPaused = false;
        queue.clear(); scanned = 0; unreached = 0; shulkersFound = 0; scanId = -1;
        if (db() == null) { abort("stash database is not enabled (set database.enabled + database.stashEnabled)"); return; }
        setBreakingAllowed(false);
        setPlacingAllowed(false);
        go(State.PLAN);
        info("StashScanner: planning the walk (break/place disabled).");
    }

    @Override
    public void onDisable() {
        if (BARITONE.isActive()) BARITONE.stop();
        restoreBreaking();
        restorePlacing();
        state = State.IDLE;
    }

    private void onStarting(ClientBotTick.Starting event) {
        if (state != State.IDLE && !complete) onEnable();   // (re)connected mid-walk: restart cleanly
    }

    private void go(State s) { state = s; step = 0; timer = 0; attempts = 0; reachTicks = 0; }

    private void abort(String reason) {
        if (BARITONE.isActive()) BARITONE.stop();
        restoreBreaking(); restorePlacing();
        if (scanId != -1 && db() != null) db().abortScan(scanId);
        paused = true; state = State.IDLE;
        warn("StashScanner paused: {}.", reason);
        inGameAlertActivePlayer("<red>StashScanner paused: " + reason);
    }

    private void finish() {
        if (BARITONE.isActive()) BARITONE.stop();
        restoreBreaking(); restorePlacing();
        if (scanId != -1 && db() != null) db().finishScan(scanId, scanned, unreached, shulkersFound);
        complete = true; state = State.IDLE;
        info("StashScanner: complete — {} chests, {} unreachable, {} shulkers.", scanned, unreached, shulkersFound);
        inGameAlertActivePlayer("<green>StashScanner done: " + scanned + " chests, " + shulkersFound + " shulkers ("
            + unreached + " unreachable)");
        CONFIG.client.extra.stashScanner.enabled = false;
        syncEnabledFromConfig();
    }

    // ---------------------------------------------------------------- tick

    private void onTick(ClientBotTick event) {
        var cfg = CONFIG.client.extra.stashScanner;
        if (notReady()) return;
        if (state == State.IDLE || complete || paused) return;

        if (cfg.pauseOnPlayer && playerNearby(cfg.playerPauseRange)) {
            if (!hazardPaused) { hazardPaused = true; if (BARITONE.isActive()) BARITONE.stop(); warn("StashScanner: player nearby - pausing."); }
            return;
        }
        if (hazardPaused) { hazardPaused = false; info("StashScanner: clear - resuming."); }

        if (timer > 0) { timer--; return; }

        switch (state) {
            case PLAN -> tickPlan();
            case SCAN -> tickScan();
            case FINALIZE -> finish();
            default -> { }
        }
    }

    private void tickPlan() {
        var cfg = CONFIG.client.extra.stashScanner;
        StashDatabase db = db();
        if (db == null) { abort("stash database is not enabled"); return; }
        queue.clear();
        if (cfg.mode == ScanMode.List) {
            for (ChestRow c : db.listChests("storage")) queue.add(new BlockPos(c.x(), c.y(), c.z()));
            if (queue.isEmpty()) { abort("list mode: no storage chests in the DB - run a region scan first"); return; }
        } else {
            BlockPos origin = (cfg.originX == 0 && cfg.originY == 0 && cfg.originZ == 0)
                ? playerFeet() : new BlockPos(cfg.originX, cfg.originY, cfg.originZ);
            // re-centre the bot's scan at the origin's feet for findBlocks, which scans around playerFeet()
            List<BlockPos> raw = findBlocksAround(origin, cfg.radius, origin.y() - cfg.bandDown, origin.y() + cfg.bandUp);
            for (BlockPos b : raw) if (!isDoubleHalfAlreadyQueued(b)) queue.add(b);
            if (queue.isEmpty()) { abort("region mode: no containers within " + cfg.radius + " blocks of the origin"); return; }
        }
        scanId = db.startScan();
        info("StashScanner: {} containers to scan (scan #{}).", queue.size(), scanId);
        go(State.SCAN);
    }

    private void tickScan() {
        var cfg = CONFIG.client.extra.stashScanner;
        if (queue.isEmpty()) { go(State.FINALIZE); return; }
        BlockPos cand = queue.get(0);
        switch (step) {
            case 0 -> {   // reach
                if (arrivedAt(new GoalNear(cand, REACH_RANGE_SQ))) {
                    if (BARITONE.isActive()) BARITONE.stop();
                    open(cand); timer = cfg.settleTicks; step = 2; attempts = 0; reachTicks = 0; return;
                }
                if (reachTicks == 0 || !BARITONE.isActive()) reachGoal = pathToNear(cand);
                if (++reachTicks > cfg.reachTimeoutTicks) { markUnreachable(cand); return; }
                timer = cfg.actionDelayTicks;
            }
            default -> {  // opened? read, then advance
                if (openContainerId() == 0) {
                    if (++attempts >= 6) { markUnreachable(cand); return; }
                    open(cand); timer = cfg.settleTicks; return;
                }
                Container c = openContainer();
                if (c != null) recordChest(cand, c);
                closeContainer();
                queue.remove(0); scanned++; step = 0; reachTicks = 0; timer = cfg.readDelayTicks;
            }
        }
    }

    private void markUnreachable(BlockPos cand) {
        if (BARITONE.isActive()) BARITONE.stop();
        StashDatabase db = db();
        if (db != null) {
            var cfg = CONFIG.client.extra.stashScanner;
            int id = db.upsertChest(cfg.dimension, cand.x(), cand.y(), cand.z(), false, "storage");
            if (id != -1) db.setChestReachable(id, false);
        }
        warn("StashScanner: {} unreachable (no break/place) - skipping.", cand);
        unreached++;
        queue.remove(0); step = 0; reachTicks = 0; attempts = 0;
    }

    /** Read + classify every shulker in the open chest window and persist the rows. */
    private void recordChest(BlockPos pos, Container c) {
        var cfg = CONFIG.client.extra.stashScanner;
        StashDatabase db = db();
        if (db == null) return;
        boolean isDouble = (c.getSize() - 36) > 27;
        int chestId = db.upsertChest(cfg.dimension, pos.x(), pos.y(), pos.z(), isDouble, "storage");
        if (chestId == -1) return;
        int chestSlots = Math.max(0, c.getSize() - 36);
        for (int i = 0; i < chestSlots; i++) {
            ItemStack s = c.getItemStack(i);
            if (!isShulkerBox(s)) continue;
            List<ContentItem> contents = toContentItems(containerContents(s));
            String name = customName(s);
            String color = colorOf(s);
            Classification cl = KitClassifier.classify(contents, name, color, db.getCatalog(), cfg.classifyStrictnessDefault);
            db.insertShulker(scanId, chestId, i, cl, name, color, contents);
            shulkersFound++;
        }
    }

    private List<ContentItem> toContentItems(List<ItemStack> raw) {
        List<ContentItem> out = new ArrayList<>();
        for (ItemStack s : raw) {
            if (s == Container.EMPTY_STACK) continue;
            String n = itemName(s);
            if (n != null) out.add(new ContentItem(n, s.getAmount()));
        }
        return out;
    }

    /** The dye colour prefix of a shulker box item name (e.g. red_shulker_box -> "red"); null if uncoloured. */
    private @Nullable String colorOf(@Nullable ItemStack s) {
        String n = itemName(s);
        if (n == null || !n.endsWith("shulker_box")) return null;
        String pre = n.substring(0, n.length() - "shulker_box".length());
        if (pre.endsWith("_")) pre = pre.substring(0, pre.length() - 1);
        return pre.isEmpty() ? null : pre;
    }

    /** Variant of {@link #findBlocks} centred on an explicit origin instead of the bot's feet. */
    private List<BlockPos> findBlocksAround(BlockPos origin, int radius, int yLo, int yHi) {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = origin.x() + dx, z = origin.z() + dz;
                if (dx * dx + dz * dz > radius * radius) continue;
                if (!World.isChunkLoadedBlockPos(x, z)) continue;
                for (int y = yLo; y <= yHi; y++) {
                    var b = World.getBlock(x, y, z);
                    if (!b.isAir() && isContainerBlockName(b.name())) out.add(new BlockPos(x, y, z));
                }
            }
        }
        return out;
    }

    /** True if a horizontally-adjacent same-name container is already queued (dedupe double-chest halves). */
    private boolean isDoubleHalfAlreadyQueued(BlockPos b) {
        String name = World.getBlock(b.x(), b.y(), b.z()).name();
        for (BlockPos q : queue) {
            if (q.y() != b.y()) continue;
            if ((Math.abs(q.x() - b.x()) == 1 && q.z() == b.z()) || (Math.abs(q.z() - b.z()) == 1 && q.x() == b.x())) {
                if (World.getBlock(q.x(), q.y(), q.z()).name().equals(name)) return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- status

    public String statusLine() {
        if (complete) return "complete (" + scanned + " chests, " + shulkersFound + " shulkers)";
        if (paused) return "paused";
        if (state == State.IDLE) return "idle";
        return state.name().toLowerCase() + " (" + scanned + "/" + (scanned + queue.size()) + " chests)";
    }
}
