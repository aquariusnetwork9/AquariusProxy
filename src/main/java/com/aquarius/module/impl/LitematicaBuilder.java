package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.cache.data.inventory.Container;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.bridge.BridgeProtocol;
import com.aquarius.feature.bridge.BridgeWaypoint;
import com.aquarius.feature.litematica.BuildPlan;
import com.aquarius.feature.litematica.BuildPlan.Placement;
import com.aquarius.feature.litematica.Schematic;
import com.aquarius.feature.litematica.SchematicFormat;
import com.aquarius.feature.pathfinder.goals.GoalNear;
import com.aquarius.feature.player.World;
import com.aquarius.mc.block.BlockPos;
import com.aquarius.mc.item.ItemData;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.BARITONE;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

/**
 * Litematica auto-builder. Loads a {@code .litematic} / {@code .nbt} schematic, then paths (via Baritone) and
 * places blocks to reconstruct it, re-supplying materials from nearby chests and placed shulker boxes when the
 * inventory runs dry. The world-interaction primitives (place / path-to-container / open / withdraw / close) are
 * inherited from {@link AbstractFieldModule}; this class only owns the tick state machine, modelled on
 * {@link AquariusMiner}'s polling design (reconnect grace, retry/timeout guards for lag-rejected actions).
 *
 * <p>v1 places full solid blocks only ({@code BARITONE.placeBlock} is item-only, so orientation / block-states are
 * not reproduced) and compares placed-vs-target by block name. Display + control also reach the ProxyBridge client
 * mod: a {@code litematica.blocks} waypoint source (origin + next targets) and a {@code litematica/start} inbound
 * command.
 */
public class LitematicaBuilder extends AbstractFieldModule {

    public enum Phase { IDLE, BUILD, RESTOCK, PAUSED, COMPLETE }

    private enum RestockPhase { FIND, PATH, OPEN, WITHDRAW, CLOSE }

    public static final String WP_GROUP = "litematica.blocks";
    public static final String TOPIC_START = "litematica/start";

    private static final int RECONNECT_GRACE_TICKS = 100;  // ~5s settle after the bot's chunk loads
    private static final int PLACE_TIMEOUT_TICKS = 20 * 15; // a single place+path may take a while on 2b2t
    private static final int PATH_TIMEOUT_TICKS = 20 * 20;  // walking to a restock container
    private static final int OPEN_TIMEOUT_TICKS = 40;       // ~2s for a container window to open/close
    private static final int MAX_WAYPOINTS = 64;

    private volatile @Nullable Schematic schematic;
    private volatile @Nullable BuildPlan plan;
    private volatile Phase phase = Phase.IDLE;

    private int reconnectGrace = 0;
    private boolean placingPushed = false;

    // build sub-state
    private @Nullable Placement current;
    private int actionTicks = 0;
    private int retries = 0;

    // restock sub-state
    private RestockPhase restockPhase = RestockPhase.FIND;
    private @Nullable BlockPos restockContainer;
    private @Nullable GoalNear restockGoal;
    private int restockStepTicks = 0;
    private final Set<BlockPos> visitedContainers = new HashSet<>();
    private Set<String> neededItems = Set.of();

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.litematica.enabled;
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
        Bridge bridge = MODULE.get(Bridge.class);
        if (bridge != null) {
            bridge.registerWaypointSource(WP_GROUP, this::waypoints);
            bridge.registerInboundHandler(TOPIC_START, this::onBridgeStart);
        }
        setPlacingAllowed(true);
        placingPushed = true;
        resetRuntime();
        phase = (plan != null && CONFIG.client.extra.litematica.originSet) ? Phase.BUILD : Phase.IDLE;
        reconnectGrace = 0;
    }

    @Override
    public void onDisable() {
        Bridge bridge = MODULE.get(Bridge.class);
        if (bridge != null) bridge.unregisterWaypointSource(WP_GROUP);
        if (BARITONE.isActive()) BARITONE.stop();
        if (placingPushed) { restorePlacing(); placingPushed = false; }
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

    /** List schematic files available in the configured directory. */
    public List<String> listSchematics() {
        File dir = new File(CONFIG.client.extra.litematica.schematicsDir);
        String[] names = dir.list((d, n) -> SchematicFormat.isSupported(n));
        if (names == null) return List.of();
        Arrays.sort(names);
        return Arrays.asList(names);
    }

    /** Parse and hold a schematic. Returns {@code null} on success, else an error message. */
    public synchronized @Nullable String load(String file) {
        try {
            Path p = Path.of(CONFIG.client.extra.litematica.schematicsDir, file);
            Schematic s = SchematicFormat.load(p);
            this.schematic = s;
            this.plan = null;
            CONFIG.client.extra.litematica.schematicFile = file;
            info("Loaded schematic '{}' — {}x{}x{}, {} blocks ({} unplaceable skipped).",
                s.name(), s.sizeX(), s.sizeY(), s.sizeZ(), s.totalBlocks(), s.skipped());
            return null;
        } catch (Exception e) {
            warn("Failed to load schematic '{}': {}", file, e.toString());
            return e.getMessage() != null ? e.getMessage() : e.toString();
        }
    }

    /** Begin (or restart) the build at the configured origin. Returns {@code null} on success, else an error. */
    public synchronized @Nullable String start() {
        var cfg = CONFIG.client.extra.litematica;
        if (schematic == null) return "No schematic loaded. Use `load <file>` first.";
        if (!cfg.originSet) return "Origin not set. Use `origin <x y z>` or `origin here`.";
        cfg.enabled = true;
        syncEnabledFromConfig();              // ensure the module is subscribed (onEnable resets runtime)
        plan = new BuildPlan(schematic, cfg.originX, cfg.originY, cfg.originZ);
        resetRuntime();
        phase = Phase.BUILD;
        info("Build started at {} {} {} — {} placements.", cfg.originX, cfg.originY, cfg.originZ, plan.total());
        return null;
    }

    /** Soft-pause the build (stays armed; resume with start). */
    public synchronized void pause() {
        if (phase == Phase.BUILD || phase == Phase.RESTOCK) {
            phase = Phase.PAUSED;
            if (BARITONE.isActive()) BARITONE.stop();
            info("Build paused.");
        }
    }

    /** Stop and disarm the module entirely. */
    public synchronized void stop() {
        CONFIG.client.extra.litematica.enabled = false;
        phase = Phase.IDLE;
        syncEnabledFromConfig();              // disables -> onDisable cleans up
        info("Build stopped.");
    }

    public Phase phase() { return phase; }
    public @Nullable Schematic schematic() { return schematic; }
    public @Nullable BuildPlan plan() { return plan; }

    // ---------------------------------------------------------------- tick state machine

    private void onTick(ClientBotTick event) {
        var cfg = CONFIG.client.extra.litematica;
        if (notReady()) return;
        if (reconnectGrace > 0) {
            int cx = (int) Math.floor(CACHE.getPlayerCache().getX()) >> 4;
            int cz = (int) Math.floor(CACHE.getPlayerCache().getZ()) >> 4;
            if (!World.isChunkLoadedChunkPos(cx, cz)) return;
            if (--reconnectGrace > 0) return;
        }
        if (phase == Phase.IDLE || phase == Phase.PAUSED || phase == Phase.COMPLETE) return;
        BuildPlan p = plan;
        if (p == null) { phase = Phase.IDLE; return; }

        // soft-pause while a non-self player is nearby (auto-resumes when clear)
        if (cfg.pauseOnPlayer && playerNearby(cfg.playerPauseRange)) {
            if (BARITONE.isActive()) BARITONE.stop();
            return;
        }
        if (inventoryBusy()) return;          // let queued container clicks drain (paced by the inventory manager)

        switch (phase) {
            case BUILD -> buildTick(p);
            case RESTOCK -> restockTick(cfg, p);
            default -> { }
        }
    }

    private void buildTick(BuildPlan p) {
        var cfg = CONFIG.client.extra.litematica;
        if (current == null) {
            if (p.isComplete()) { complete(); return; }
            current = p.next(this::haveItem);
            if (current == null) { beginRestock(p); return; }
            issuePlace();
            return;
        }
        // a place is in flight for `current`
        if (satisfiedNow(current)) {
            current = null;
            retries = 0;
            actionTicks = 0;
            return;
        }
        if (BARITONE.isActive()) {
            if (++actionTicks > PLACE_TIMEOUT_TICKS) { BARITONE.stop(); failCurrent(cfg, p); }
            return;
        }
        // Baritone went idle but the block isn't there yet: settle briefly, then retry or skip.
        if (++actionTicks > cfg.settleTicks) failCurrent(cfg, p);
    }

    private void issuePlace() {
        Placement c = current;
        if (c == null) return;
        place(new BlockPos(c.x(), c.y(), c.z()), c.entry().placeItem());
        actionTicks = 0;
    }

    private void failCurrent(com.aquarius.util.config.Config.Client.Extra.Litematica cfg, BuildPlan p) {
        retries++;
        actionTicks = 0;
        if (retries > cfg.maxPlaceRetries) {
            Placement c = current;
            if (c != null) {
                warn("Skipping unplaceable block {} at {} {} {} after {} attempts.",
                    c.entry().blockName(), c.x(), c.y(), c.z(), retries - 1);
                p.skip(c);
            }
            current = null;
            retries = 0;
        } else {
            issuePlace();                      // retry the same target
        }
    }

    private boolean satisfiedNow(Placement p) {
        return World.getBlock(p.x(), p.y(), p.z()).name().equals(p.entry().blockName());
    }

    private boolean haveItem(ItemData it) {
        return findInInv(s -> matchesName(s, it.name())) != -1;
    }

    private void complete() {
        phase = Phase.COMPLETE;
        if (BARITONE.isActive()) BARITONE.stop();
        info("Litematica build complete.");
        inGameAlertActivePlayer("<green>Litematica build complete");
    }

    // ---------------------------------------------------------------- restock

    private void beginRestock(BuildPlan p) {
        neededItems = itemNames(p.remainingMaterials());
        if (neededItems.isEmpty()) {           // nothing placeable remains -> we're effectively done
            complete();
            return;
        }
        phase = Phase.RESTOCK;
        restockPhase = RestockPhase.FIND;
        restockStepTicks = 0;
        restockContainer = null;
        restockGoal = null;
    }

    private void restockTick(com.aquarius.util.config.Config.Client.Extra.Litematica cfg, BuildPlan p) {
        switch (restockPhase) {
            case FIND -> {
                BlockPos c = nearestUnvisitedContainer(cfg);
                if (c == null) { pauseOut("out of materials (no nearby container has the needed items)"); return; }
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
                    restockPhase = RestockPhase.FIND;   // give up on this container, try the next
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
                if (c == null) { restockPhase = RestockPhase.FIND; return; } // closed unexpectedly
                if (emptyMainSlots() <= 0) { closeContainer(); restockPhase = RestockPhase.CLOSE; restockStepTicks = 0; return; }
                int slot = findContainerSlot(c, this::isNeeded);
                if (slot < 0) { closeContainer(); restockPhase = RestockPhase.CLOSE; restockStepTicks = 0; return; }
                shiftClick(c, slot);             // one stack per inventory-manager tick (gated by inventoryBusy)
            }
            case CLOSE -> {
                if (openContainer() == null) {
                    phase = Phase.BUILD;         // try building again; a future shortage re-scans unvisited containers
                    restockPhase = RestockPhase.FIND;
                } else if (++restockStepTicks > OPEN_TIMEOUT_TICKS) {
                    closeContainer();
                }
            }
        }
    }

    private @Nullable BlockPos nearestUnvisitedContainer(com.aquarius.util.config.Config.Client.Extra.Litematica cfg) {
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

    private boolean isRestockContainerName(com.aquarius.util.config.Config.Client.Extra.Litematica cfg, String name) {
        if (cfg.restockFromChests && isContainerBlockName(name)) return true;
        return cfg.restockFromShulkers && name.endsWith("shulker_box");
    }

    private boolean isNeeded(ItemStack s) {
        String n = itemName(s);
        return n != null && neededItems.contains(n);
    }

    private void pauseOut(String reason) {
        phase = Phase.PAUSED;
        if (BARITONE.isActive()) BARITONE.stop();
        warn("Litematica paused: {}", reason);
        inGameAlertActivePlayer("<yellow>Litematica paused: " + reason);
    }

    private static Set<String> itemNames(List<Schematic.MaterialEntry> materials) {
        Set<String> out = new HashSet<>();
        for (Schematic.MaterialEntry m : materials) out.add(m.item());
        return out;
    }

    // ---------------------------------------------------------------- ProxyBridge "open apps"

    private List<BridgeWaypoint> waypoints() {
        BuildPlan p = plan;
        if (p == null || phase == Phase.IDLE) return List.of();
        var cfg = CONFIG.client.extra.litematica;
        String dim = World.getCurrentDimension().name();
        List<BridgeWaypoint> out = new ArrayList<>();
        out.add(new BridgeWaypoint("lite_origin", "Build Origin", dim,
            cfg.originX, cfg.originY, cfg.originZ, BridgeWaypoint.COLOR_GREEN, 0));
        for (Placement pl : p.upcoming(MAX_WAYPOINTS)) {
            out.add(new BridgeWaypoint("lite_" + pl.x() + "_" + pl.y() + "_" + pl.z(), "Block", dim,
                pl.x(), pl.y(), pl.z(), BridgeWaypoint.COLOR_AQUA, 0));
        }
        return out;
    }

    /** Inbound bridge command: {@code String file [, int x, int y, int z]} — load + start a build. */
    private void onBridgeStart(ServerSession session, BridgeProtocol.Reader r) {
        try {
            String file = r.readString();
            if (r.hasRemaining()) {
                var cfg = CONFIG.client.extra.litematica;
                cfg.originX = r.readInt();
                cfg.originY = r.readInt();
                cfg.originZ = r.readInt();
                cfg.originSet = true;
            }
            String err = load(file);
            if (err == null) err = start();
            if (err != null) warn("Bridge litematica/start rejected: {}", err);
        } catch (Exception e) {
            warn("Failed to handle bridge litematica/start", e);
        }
    }
}
