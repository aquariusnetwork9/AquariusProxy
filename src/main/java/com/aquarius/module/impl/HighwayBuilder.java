package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.cache.data.inventory.Container;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.pathfinder.goals.GoalNear;
import com.aquarius.feature.player.World;
import com.aquarius.mc.block.BlockPos;
import com.aquarius.mc.item.ItemData;
import com.aquarius.util.config.Config;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.BARITONE;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;

/**
 * HighwayBuilder — automated nether-highway construction, modelled on Meteor Client's module. Lays an obsidian
 * road (and clears the head/flight room above it) in one of the 8 highway directions, restocking the building
 * block from nearby chests / placed shulker boxes when the inventory runs dry.
 *
 * <p>The road profile is generated procedurally as an ordered stream of break/place "jobs" along the direction;
 * {@link AbstractFieldModule}'s {@code break}/{@code place} primitives drive Baritone, which paths the bot to each
 * target — so this class only owns the geometry and a tick state machine (modelled on {@link LitematicaBuilder}'s:
 * reconnect grace, in-flight settle, retry/skip on lag-rejected actions, and a copy of its restock cycle).
 */
public class HighwayBuilder extends AbstractFieldModule {

    public enum Phase { IDLE, BUILD, RESTOCK, PAUSED, COMPLETE }

    private enum RestockPhase { FIND, PATH, OPEN, WITHDRAW, CLOSE }

    private enum Action { BREAK, PLACE }

    private enum CellKind { FLOOR, CLEAR, WALL }

    private record Cell(CellKind kind, BlockPos pos) { }

    private record Job(Action action, BlockPos pos) { }

    private static final int RECONNECT_GRACE_TICKS = 100;     // ~5s settle after the bot's chunk loads
    private static final int ACTION_TIMEOUT_TICKS = 20 * 15;   // a single break/place + path may take a while on 2b2t
    private static final int PATH_TIMEOUT_TICKS = 20 * 20;     // walking to a restock container
    private static final int OPEN_TIMEOUT_TICKS = 40;          // ~2s for a container window to open/close
    private static final int WORLD_BORDER = 29_950_000;        // stop short of the 30M nether border

    private volatile Phase phase = Phase.IDLE;

    // direction + geometry, resolved on start
    private int dx, dz, perpX, perpZ;        // forward step + perpendicular (width) step
    private int roadY;                       // road surface Y
    private double stepLen = 1.0;            // blocks per forward index (1 axis, √2 diagonal)
    private long targetLen = Long.MAX_VALUE;  // number of forward indices to build (∞ until stopped)

    // build cursor
    private long headIndex = 0;              // current forward index being built
    private int slot = 0;                    // index into the current profile's cell list
    private @Nullable Job current;
    private int actionTicks = 0;
    private int retries = 0;
    private long placed = 0, cleared = 0, skipped = 0;

    private int reconnectGrace = 0;
    private boolean gatesPushed = false;

    // restock sub-state (copied from LitematicaBuilder)
    private RestockPhase restockPhase = RestockPhase.FIND;
    private @Nullable BlockPos restockContainer;
    private @Nullable GoalNear restockGoal;
    private int restockStepTicks = 0;
    private final Set<BlockPos> visitedContainers = new HashSet<>();

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.highwayBuilder.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(ClientBotTick.Starting.class, this::onStarting)
        );
    }

    @Override
    public void onEnable() {
        setBreakingAllowed(true);     // Baritone may tunnel/bridge its own path to each target
        setPlacingAllowed(true);
        gatesPushed = true;
        resetRuntime();
        phase = CONFIG.client.extra.highwayBuilder.originSet ? Phase.BUILD : Phase.IDLE;
        reconnectGrace = 0;
    }

    @Override
    public void onDisable() {
        if (BARITONE.isActive()) BARITONE.stop();
        if (gatesPushed) { restoreBreaking(); restorePlacing(); gatesPushed = false; }
        resetRuntime();
    }

    private void resetRuntime() {
        current = null;
        actionTicks = 0;
        retries = 0;
        restockPhase = RestockPhase.FIND;
        restockContainer = null;
        restockGoal = null;
        restockStepTicks = 0;
        visitedContainers.clear();
    }

    private void onStarting(ClientBotTick.Starting event) {
        reconnectGrace = RECONNECT_GRACE_TICKS;
        if (BARITONE.isActive()) BARITONE.stop();
    }

    // ---------------------------------------------------------------- public control API

    /** Begin (or restart) building in {@code dir} from the bot's current position. Null on success, else an error. */
    public synchronized @Nullable String start(@Nullable String dir) {
        var cfg = CONFIG.client.extra.highwayBuilder;
        if (notReady()) return "Not in-game / chunk not loaded yet.";
        if (dir != null && resolveDir(dir) == null) return "Unknown direction '" + dir + "' (use n/s/e/w/ne/nw/se/sw).";
        if (dir != null) cfg.direction = canonicalDir(dir);
        int[] d = resolveDir(cfg.direction);
        if (d == null) return "Unknown direction '" + cfg.direction + "'.";
        dx = d[0]; dz = d[1]; perpX = -dz; perpZ = dx;
        stepLen = Math.sqrt((double) dx * dx + (double) dz * dz);
        cfg.originX = (int) Math.floor(CACHE.getPlayerCache().getX());
        cfg.originZ = (int) Math.floor(CACHE.getPlayerCache().getZ());
        cfg.originSet = true;
        roadY = cfg.useCurrentY ? (int) Math.floor(CACHE.getPlayerCache().getY()) - 1 : cfg.roadY;
        targetLen = cfg.targetDistance > 0 ? Math.max(1, Math.round(cfg.targetDistance / stepLen)) : Long.MAX_VALUE;
        cfg.enabled = true;
        syncEnabledFromConfig();          // ensure subscribed; onEnable resets runtime
        headIndex = 0; slot = 0;
        placed = cleared = skipped = 0;
        resetRuntime();
        phase = Phase.BUILD;
        info("Highway build started: dir={} origin={} {} {} width={} clear={} block={}{}.",
            cfg.direction, cfg.originX, roadY, cfg.originZ, cfg.width, cfg.clearHeight, cfg.block,
            cfg.targetDistance > 0 ? " target=" + cfg.targetDistance + "b" : "");
        return null;
    }

    public synchronized void pause() {
        if (phase == Phase.BUILD || phase == Phase.RESTOCK) {
            phase = Phase.PAUSED;
            if (BARITONE.isActive()) BARITONE.stop();
            info("Highway build paused.");
        }
    }

    public synchronized void stop() {
        CONFIG.client.extra.highwayBuilder.enabled = false;
        phase = Phase.IDLE;
        syncEnabledFromConfig();          // disables -> onDisable cleans up
        info("Highway build stopped.");
    }

    public Phase phase() { return phase; }
    public long built() { return headIndex; }
    public long placedCount() { return placed; }
    public long clearedCount() { return cleared; }
    public long skippedCount() { return skipped; }
    public double distanceBuilt() { return headIndex * stepLen; }

    // ---------------------------------------------------------------- tick state machine

    private void onTick(ClientBotTick event) {
        var cfg = CONFIG.client.extra.highwayBuilder;
        if (notReady()) return;
        if (reconnectGrace > 0) {
            int cx = (int) Math.floor(CACHE.getPlayerCache().getX()) >> 4;
            int cz = (int) Math.floor(CACHE.getPlayerCache().getZ()) >> 4;
            if (!World.isChunkLoadedChunkPos(cx, cz)) return;
            if (--reconnectGrace > 0) return;
        }
        if (phase == Phase.IDLE || phase == Phase.PAUSED || phase == Phase.COMPLETE) return;

        if (cfg.pauseOnPlayer && playerNearby(cfg.playerPauseRange)) {
            if (BARITONE.isActive()) BARITONE.stop();
            return;
        }
        if (inventoryBusy()) return;      // let queued container clicks drain

        switch (phase) {
            case BUILD -> buildTick(cfg);
            case RESTOCK -> restockTick(cfg);
            default -> { }
        }
    }

    private void buildTick(Config.Client.Extra.HighwayBuilder cfg) {
        if (current == null) {
            Job j = nextJob(cfg);
            if (j == null) { complete(); return; }
            if (j.action() == Action.PLACE && !haveBlock(cfg)) { beginRestock(); return; }
            current = j;
            issue(cfg, j);
            actionTicks = 0;
            retries = 0;
            return;
        }
        if (satisfied(current)) {
            if (current.action() == Action.PLACE) placed++; else cleared++;
            current = null;
            return;
        }
        if (BARITONE.isActive()) {
            if (++actionTicks > ACTION_TIMEOUT_TICKS) { BARITONE.stop(); failCurrent(cfg); }
            return;
        }
        // Baritone went idle but the block isn't there yet: settle briefly, then retry or skip.
        if (++actionTicks > cfg.settleTicks) failCurrent(cfg);
    }

    private void issue(Config.Client.Extra.HighwayBuilder cfg, Job j) {
        if (j.action() == Action.BREAK) {
            breakAt(j.pos(), true);
        } else {
            int s = findInInv(st -> matchesName(st, cfg.block));
            if (s < 0) { beginRestock(); return; }
            place(j.pos(), itemDataAt(s));
        }
    }

    private boolean satisfied(Job j) {
        return j.action() == Action.BREAK ? isAir(j.pos()) : isBuildBlock(j.pos());
    }

    private void failCurrent(Config.Client.Extra.HighwayBuilder cfg) {
        retries++;
        actionTicks = 0;
        if (retries > cfg.maxRetries) {
            Job c = current;
            if (c != null) warn("Skipping {} at {} {} {} after {} attempts.",
                c.action(), c.pos().x(), c.pos().y(), c.pos().z(), retries - 1);
            slot++;            // give up on this profile cell, move to the next
            skipped++;
            current = null;
            retries = 0;
        } else {
            issue(cfg, current);   // retry the same target
        }
    }

    /**
     * The next unsatisfied break/place job, advancing the (headIndex, slot) cursor over already-built cells.
     * For a place target that is currently a wrong solid, a BREAK is returned first; once it's air, the re-scan
     * returns the PLACE. Null when the target length / world border is reached.
     */
    private @Nullable Job nextJob(Config.Client.Extra.HighwayBuilder cfg) {
        while (headIndex < targetLen) {
            int cx = cfg.originX + (int) (headIndex * dx);
            int cz = cfg.originZ + (int) (headIndex * dz);
            if (Math.abs(cx) >= WORLD_BORDER || Math.abs(cz) >= WORLD_BORDER) return null;
            List<Cell> cells = profileCells(cfg, cx, cz);
            while (slot < cells.size()) {
                Cell cell = cells.get(slot);
                if (cellSatisfied(cfg, cell)) { slot++; continue; }
                if (cell.kind() == CellKind.CLEAR) return new Job(Action.BREAK, cell.pos());
                // FLOOR / WALL place target: clear a wrong solid first, else place
                return isAir(cell.pos()) ? new Job(Action.PLACE, cell.pos()) : new Job(Action.BREAK, cell.pos());
            }
            headIndex++;
            slot = 0;
        }
        return null;
    }

    /** Ordered cells of one forward index: the floor row first (footing), then the head clearance / edge walls. */
    private List<Cell> profileCells(Config.Client.Extra.HighwayBuilder cfg, int cx, int cz) {
        List<Cell> cells = new ArrayList<>();
        if (cfg.floor) {
            for (int w = -cfg.width; w <= cfg.width; w++) {
                cells.add(new Cell(CellKind.FLOOR, new BlockPos(cx + w * perpX, roadY, cz + w * perpZ)));
            }
        }
        for (int w = -cfg.width; w <= cfg.width; w++) {
            int fx = cx + w * perpX, fz = cz + w * perpZ;
            boolean edge = cfg.walls && Math.abs(w) == cfg.width;
            if (edge) {
                cells.add(new Cell(CellKind.WALL, new BlockPos(fx, roadY + 1, fz)));   // railing, no clearance above
            } else {
                for (int h = 1; h <= cfg.clearHeight; h++) {
                    cells.add(new Cell(CellKind.CLEAR, new BlockPos(fx, roadY + h, fz)));
                }
            }
        }
        return cells;
    }

    private boolean cellSatisfied(Config.Client.Extra.HighwayBuilder cfg, Cell c) {
        return c.kind() == CellKind.CLEAR ? isAir(c.pos()) : isBuildBlock(c.pos());
    }

    private boolean isBuildBlock(BlockPos p) {
        return World.getBlock(p.x(), p.y(), p.z()).name().equals(CONFIG.client.extra.highwayBuilder.block);
    }

    private boolean haveBlock(Config.Client.Extra.HighwayBuilder cfg) {
        return findInInv(s -> matchesName(s, cfg.block)) != -1;
    }

    private void complete() {
        phase = Phase.COMPLETE;
        if (BARITONE.isActive()) BARITONE.stop();
        info("Highway build complete — {} blocks placed, {} cleared, {} skipped (~{} blocks).",
            placed, cleared, skipped, Math.round(distanceBuilt()));
        inGameAlertActivePlayer("<green>Highway build complete");
    }

    // ---------------------------------------------------------------- direction helpers

    private static @Nullable int[] resolveDir(String s) {
        return switch (s.toLowerCase().trim()) {
            case "n", "north" -> new int[] {0, -1};
            case "s", "south" -> new int[] {0, 1};
            case "e", "east" -> new int[] {1, 0};
            case "w", "west" -> new int[] {-1, 0};
            case "ne", "northeast" -> new int[] {1, -1};
            case "nw", "northwest" -> new int[] {-1, -1};
            case "se", "southeast" -> new int[] {1, 1};
            case "sw", "southwest" -> new int[] {-1, 1};
            default -> null;
        };
    }

    private static String canonicalDir(String s) {
        return switch (s.toLowerCase().trim()) {
            case "n", "north" -> "north";
            case "s", "south" -> "south";
            case "e", "east" -> "east";
            case "w", "west" -> "west";
            case "ne", "northeast" -> "ne";
            case "nw", "northwest" -> "nw";
            case "se", "southeast" -> "se";
            case "sw", "southwest" -> "sw";
            default -> s;
        };
    }

    // ---------------------------------------------------------------- restock (mirrors LitematicaBuilder)

    private void beginRestock() {
        phase = Phase.RESTOCK;
        restockPhase = RestockPhase.FIND;
        restockStepTicks = 0;
        restockContainer = null;
        restockGoal = null;
    }

    private void restockTick(Config.Client.Extra.HighwayBuilder cfg) {
        switch (restockPhase) {
            case FIND -> {
                BlockPos c = nearestUnvisitedContainer(cfg);
                if (c == null) { pauseOut("out of '" + cfg.block + "' (no nearby container has it)"); return; }
                restockContainer = c;
                visitedContainers.add(c);
                restockGoal = pathToNear(c);
                restockPhase = RestockPhase.PATH;
                restockStepTicks = 0;
            }
            case PATH -> {
                if (arrivedAt(restockGoal)) {
                    if (BARITONE.isActive()) BARITONE.stop();
                    open(restockContainer);
                    restockPhase = RestockPhase.OPEN;
                    restockStepTicks = 0;
                } else if (++restockStepTicks > PATH_TIMEOUT_TICKS) {
                    if (BARITONE.isActive()) BARITONE.stop();
                    restockPhase = RestockPhase.FIND;
                }
            }
            case OPEN -> {
                if (openContainer() != null) {
                    restockPhase = RestockPhase.WITHDRAW;
                    restockStepTicks = 0;
                } else if (++restockStepTicks > OPEN_TIMEOUT_TICKS) {
                    restockPhase = RestockPhase.FIND;
                }
            }
            case WITHDRAW -> {
                Container c = openContainer();
                if (c == null) { restockPhase = RestockPhase.FIND; return; }
                if (emptyMainSlots() <= 0) { closeContainer(); restockPhase = RestockPhase.CLOSE; restockStepTicks = 0; return; }
                int s = findContainerSlot(c, st -> matchesName(st, cfg.block));
                if (s < 0) { closeContainer(); restockPhase = RestockPhase.CLOSE; restockStepTicks = 0; return; }
                shiftClick(c, s);   // one stack per inventory-manager tick (gated by inventoryBusy)
            }
            case CLOSE -> {
                if (openContainer() == null) {
                    phase = Phase.BUILD;     // resume building; a future shortage re-scans unvisited containers
                    restockPhase = RestockPhase.FIND;
                } else if (++restockStepTicks > OPEN_TIMEOUT_TICKS) {
                    closeContainer();
                }
            }
        }
    }

    private @Nullable BlockPos nearestUnvisitedContainer(Config.Client.Extra.HighwayBuilder cfg) {
        int fy = playerFeet().y();
        List<BlockPos> all = findBlocks(cfg.restockRadius, fy - cfg.restockVerticalRange, fy + cfg.restockVerticalRange,
            n -> isRestockContainerName(cfg, n));
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos b : all) {
            if (visitedContainers.contains(b)) continue;
            double d = distToBot(b);
            if (d < bestD) { bestD = d; best = b; }
        }
        return best;
    }

    private boolean isRestockContainerName(Config.Client.Extra.HighwayBuilder cfg, String name) {
        if (cfg.restockFromChests && isContainerBlockName(name)) return true;
        return cfg.restockFromShulkers && name.endsWith("shulker_box");
    }

    private void pauseOut(String reason) {
        phase = Phase.PAUSED;
        if (BARITONE.isActive()) BARITONE.stop();
        warn("Highway build paused: {}", reason);
        inGameAlertActivePlayer("<yellow>Highway build paused: " + reason);
    }
}
