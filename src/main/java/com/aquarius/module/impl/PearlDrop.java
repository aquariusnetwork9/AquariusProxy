package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.cache.data.inventory.Container;
import com.aquarius.discord.Embed;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.inventory.InventoryActionRequest;
import com.aquarius.feature.inventory.actions.InventoryAction;
import com.aquarius.feature.inventory.actions.MoveToHotbarSlot;
import com.aquarius.feature.inventory.actions.SetHeldItem;
import com.aquarius.feature.pathfinder.goals.GoalBlock;
import com.aquarius.feature.player.ClickTarget;
import com.aquarius.feature.player.Input;
import com.aquarius.feature.player.InputRequest;
import com.aquarius.feature.player.World;
import com.aquarius.mc.block.Block;
import com.aquarius.mc.block.BlockPos;
import com.aquarius.mc.block.BlockRegistry;
import com.aquarius.mc.block.BlockTags;
import com.aquarius.mc.block.properties.api.BlockStateProperties;
import com.aquarius.mc.item.ItemRegistry;
import com.aquarius.module.api.Module;
import com.aquarius.util.math.MathHelper;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.*;

/**
 * PearlDrop — deposits ender pearls INTO pearl stasis chambers (the counterpart to {@link PearlManager}'s
 * pull/load side). A stasis chamber is a 1x1 vertical water/bubble column at least
 * {@code minWaterDepth} deep with a soul-sand block at the bottom (which turns the column into an upward
 * bubble column that holds a thrown pearl) and a trapdoor on top — either a waterlogged trapdoor capping
 * the top water cell, or a dry trapdoor placed directly above the column.
 *
 * <p>To deposit, the bot pathfinds to a standable rim block beside the column, then takes manual control of
 * the {@code INPUTS} pipeline: it sneaks and presses toward the column so vanilla sneak ledge-protection lets
 * it overhang the opening without falling in, aims its look vector at the centre of the soul-sand block, and
 * uses (throws) a held pearl straight down into the column. Afterward it backs onto the rim and releases.
 *
 * <p>It can {@link #scan(int) scan} loaded chunks for chambers (anchored on soul sand, loaded-chunks-only),
 * deposit into chambers picked by the operator, or be driven by other modules (e.g. a stash-mover) as a
 * service via {@link #beginDepositNear} / {@link #isDepositDone}. All settings live under
 * {@code CONFIG.client.extra.pearlDrop}.
 */
public class PearlDrop extends Module {

    /** Result of the most recent (or in-progress) deposit run, surfaced to callers + the command. */
    public enum DepositResult { IDLE, RUNNING, SUCCESS, PARTIAL, NO_PEARLS, NO_RIM, NO_CHAMBER, OCCUPIED, ABORTED }

    /** A detected pearl stasis chamber. {@code trapdoorY} is the trapdoor (column-top) cell. */
    public record StasisChamber(int columnX, int columnZ, int soulSandY, int trapdoorY,
                                int depth, boolean trapdoorOpen, boolean waterlogged) { }

    private enum Phase { IDLE, CLOSE_TRAPDOOR, PATH_TO_RIM, APPROACH, THROW, RETREAT, NEXT, DONE }

    private enum PearlState { NONE, NOT_READY, READY }

    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private record Job(StasisChamber chamber, int count) { }

    private final Deque<Job> queue = new ArrayDeque<>();
    private final List<StasisChamber> lastScan = new ArrayList<>();

    private Phase phase = Phase.IDLE;
    private Job job;
    private BlockPos rim;
    private double rimFeetY;
    private DepositResult result = DepositResult.IDLE;

    private int phaseTicks;
    private boolean pathRequested;
    private boolean trapActionRequested;
    private volatile boolean trapClosed;

    private double lastX, lastZ;
    private boolean haveLast;
    private int stallTicks;

    private int targetCount;
    private int thrownThisChamber;
    private int pearlCountBefore;
    private boolean clickSent;
    private int throwWait;
    private int throwRetries;
    private int spacingWait;

    private int totalThrown, totalChambers, totalFailed;

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.pearlDrop.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(ClientBotTick.Stopped.class, e -> abortAll("disconnected"))
        );
    }

    @Override
    public void onDisable() {
        abortAll("disabled");
    }

    // ------------------------------------------------------------------ service API

    /** Scan loaded chunks within {@code radius} (and +/- {@code scanYRange}) for stasis chambers. */
    public synchronized List<StasisChamber> scan(int radius) {
        lastScan.clear();
        int yr = CONFIG.client.extra.pearlDrop.scanYRange;
        lastScan.addAll(chambersInBox(playerX(), playerY(), playerZ(), radius, yr, yr));
        return getLastScan();
    }

    public List<StasisChamber> getLastScan() {
        return new ArrayList<>(lastScan);
    }

    public boolean isOccupied(StasisChamber c) {
        return occupied(c);
    }

    public boolean hasRim(StasisChamber c) {
        return findRim(c) != null;
    }

    /** True once all queued chambers are processed (or nothing is running). */
    public boolean isDepositDone() {
        return phase == Phase.IDLE || phase == Phase.DONE;
    }

    public DepositResult lastResult() {
        return result;
    }

    /**
     * Targeted deposit: find a chamber within {@code coordTolerance} of the given coords and deposit into it.
     * If several chambers match and one is unoccupied, the unoccupied one is taken.
     * @return true if a deposit was queued.
     */
    public synchronized boolean beginDepositNear(int nearX, int nearY, int nearZ, int count) {
        var cfg = CONFIG.client.extra.pearlDrop;
        // search well downward so coords pointing at the chamber TOP still reach the soul sand below
        List<StasisChamber> near = chambersInBox(nearX, nearY, nearZ,
            cfg.coordTolerance, cfg.coordTolerance, cfg.coordTolerance + cfg.minWaterDepth + 8);
        if (near.isEmpty()) {
            result = DepositResult.NO_CHAMBER;
            return false;
        }
        StasisChamber best = null;
        long bestDsq = Long.MAX_VALUE;
        for (StasisChamber c : near) {
            if (occupied(c)) continue;
            long d = distSq(c.columnX() + 0.5, c.soulSandY(), c.columnZ() + 0.5, nearX, nearY, nearZ);
            if (d < bestDsq) { bestDsq = d; best = c; }
        }
        if (best == null) {
            result = DepositResult.OCCUPIED; // chambers exist but all are occupied
            return false;
        }
        return beginDeposit(best, count);
    }

    /** Queue a specific chamber for deposit and (re)start the run. */
    public synchronized boolean beginDeposit(StasisChamber chamber, int count) {
        queue.add(new Job(chamber, normalizeCount(count)));
        ensureRunning();
        return true;
    }

    /** Queue every chamber in the list; returns the number queued. */
    public synchronized int enqueueAll(List<StasisChamber> chambers, int count) {
        int n = 0;
        for (StasisChamber c : chambers) { queue.add(new Job(c, normalizeCount(count))); n++; }
        if (n > 0) ensureRunning();
        return n;
    }

    /** Queue every unoccupied, reachable chamber from the most recent scan. */
    public synchronized int dropAllEmpty(int count) {
        List<StasisChamber> empties = new ArrayList<>();
        for (StasisChamber c : lastScan) if (!occupied(c) && findRim(c) != null) empties.add(c);
        return enqueueAll(empties, count);
    }

    /** Cancel everything and release control. */
    public synchronized void stopAll() {
        abortAll("stopped by operator");
    }

    private int normalizeCount(int count) {
        return count > 0 ? count : Math.max(1, CONFIG.client.extra.pearlDrop.pearlsPerChamber);
    }

    private void ensureRunning() {
        if (!isEnabled()) {
            CONFIG.client.extra.pearlDrop.enabled = true;
            syncEnabledFromConfig();
        }
        if (phase == Phase.IDLE || phase == Phase.DONE) {
            totalThrown = totalChambers = totalFailed = 0;
            result = DepositResult.RUNNING;
            phase = Phase.NEXT;
            phaseTicks = 0;
        }
    }

    private void abortAll(String why) {
        queue.clear();
        job = null;
        boolean wasRunning = phase != Phase.IDLE && phase != Phase.DONE;
        phase = Phase.IDLE;
        BARITONE.stop();
        if (wasRunning) {
            submitRelease();
            if (result == DepositResult.RUNNING) result = DepositResult.ABORTED;
            info("PearlDrop aborted: " + why);
        }
    }

    // ------------------------------------------------------------------ tick / state machine

    private void onTick(final ClientBotTick event) {
        try {
            if (phase == Phase.IDLE || phase == Phase.DONE) return;
            int cx = MathHelper.floorI(playerX()) >> 4;
            int cz = MathHelper.floorI(playerZ()) >> 4;
            if (!World.isChunkLoadedChunkPos(cx, cz)) return; // wait for the world to load

            phaseTicks++;
            switch (phase) {
                case CLOSE_TRAPDOOR -> tickCloseTrapdoor();
                case PATH_TO_RIM    -> tickPathToRim();
                case APPROACH       -> tickApproach();
                case THROW          -> tickThrow();
                case RETREAT        -> tickRetreat();
                case NEXT           -> tickNext();
                default             -> { }
            }
        } catch (final Exception e) {
            error("PearlDrop tick error", e);
            abortAll("internal error");
        }
    }

    private StasisChamber chamber() {
        return job.chamber();
    }

    private void tickNext() {
        BARITONE.stop();
        var cfg = CONFIG.client.extra.pearlDrop;
        while (true) {
            job = queue.poll();
            if (job == null) { finish(); return; }
            StasisChamber c = chamber();
            if (occupied(c)) {
                totalFailed++;
                result = DepositResult.OCCUPIED;
                info("Skipping occupied chamber at " + coordStr(c));
                continue;
            }
            BlockPos r = findRim(c);
            if (r == null) {
                totalFailed++;
                result = DepositResult.NO_RIM;
                info("No standable rim for chamber at " + coordStr(c));
                continue;
            }
            rim = r;
            rimFeetY = r.y();
            targetCount = job.count();
            thrownThisChamber = 0;
            pathRequested = false;
            trapActionRequested = false;
            trapClosed = false;
            haveLast = false;
            stallTicks = 0;
            phaseTicks = 0;
            phase = (c.trapdoorOpen() && cfg.closeOpenTrapdoor) ? Phase.CLOSE_TRAPDOOR : Phase.PATH_TO_RIM;
            return;
        }
    }

    private void tickCloseTrapdoor() {
        StasisChamber c = chamber();
        if (!trapActionRequested) {
            BARITONE.rightClickBlock(c.columnX(), c.trapdoorY(), c.columnZ())
                .addExecutedListener(f -> trapClosed = true);
            trapActionRequested = true;
            phaseTicks = 0;
        }
        if (trapClosed || phaseTicks > CONFIG.client.extra.pearlDrop.pathTimeoutTicks) {
            BARITONE.stop();
            phase = Phase.PATH_TO_RIM;
            pathRequested = false;
            phaseTicks = 0;
        }
    }

    private void tickPathToRim() {
        var cfg = CONFIG.client.extra.pearlDrop;
        if (!pathRequested) {
            BARITONE.pathTo(new GoalBlock(rim));
            pathRequested = true;
            phaseTicks = 0;
        }
        if (atRim()) {
            BARITONE.stop();
            phase = Phase.APPROACH;
            haveLast = false;
            stallTicks = 0;
            phaseTicks = 0;
            return;
        }
        if (phaseTicks > cfg.pathTimeoutTicks) {
            totalFailed++;
            skipChamber(DepositResult.NO_RIM, "couldn't path to the rim of " + coordStr(chamber()));
        }
    }

    private void tickApproach() {
        var cfg = CONFIG.client.extra.pearlDrop;
        StasisChamber c = chamber();
        double x = playerX(), y = playerY(), z = playerZ();
        if (fell(y)) { abortOverhang("fell off the rim at " + coordStr(c)); return; }

        float yaw = yawToColumn(x, z, c);
        float pitch = pitchToSoulSand(x, y, z, c);
        submitMove(true, false, true, false, yaw, pitch); // forward + sneak: pin to the ledge

        double move = haveLast ? Math.hypot(x - lastX, z - lastZ) : 1.0;
        lastX = x; lastZ = z; haveLast = true;
        double hd = horizDist(x, z, c.columnX() + 0.5, c.columnZ() + 0.5);
        if (move < 0.02 && hd < 1.2) stallTicks++; else stallTicks = 0;

        if (stallTicks >= cfg.centerStallTicks) { startThrow(); return; }
        if (phaseTicks > cfg.approachTimeoutTicks) {
            if (hd < 1.3) startThrow();
            else abortOverhang("couldn't centre over " + coordStr(c));
        }
    }

    private void startThrow() {
        thrownThisChamber = 0;
        clickSent = false;
        throwWait = 0;
        throwRetries = 0;
        spacingWait = 0;
        pearlCountBefore = countPearls();
        phaseTicks = 0;
        phase = Phase.THROW;
    }

    private void tickThrow() {
        var cfg = CONFIG.client.extra.pearlDrop;
        StasisChamber c = chamber();
        double x = playerX(), y = playerY(), z = playerZ();
        if (fell(y)) { abortOverhang("fell off the rim at " + coordStr(c)); return; }

        float yaw = yawToColumn(x, z, c);
        float pitch = pitchToSoulSand(x, y, z, c);

        if (spacingWait > 0) { // cooldown pacing between pearls — hold the ledge, no click
            spacingWait--;
            submitMove(true, false, true, false, yaw, pitch);
            return;
        }

        PearlState ps = ensurePearlHeld();
        if (ps == PearlState.NONE) {
            if (thrownThisChamber > 0) finishChamberPartial("ran out of pearls");
            else { result = DepositResult.NO_PEARLS; abortOverhang("no pearls to throw"); }
            return;
        }
        if (ps == PearlState.NOT_READY) { // selecting/moving the pearl into hand
            submitMove(true, false, true, false, yaw, pitch);
            return;
        }

        if (!clickSent) {
            pearlCountBefore = countPearls();
            submitMove(true, false, true, true, yaw, pitch); // rightClick = use/throw the pearl
            clickSent = true;
            throwWait = 0;
            return;
        }

        submitMove(true, false, true, false, yaw, pitch);
        throwWait++;
        if (countPearls() < pearlCountBefore) {
            thrownThisChamber++;
            totalThrown++;
            info("Threw pearl " + thrownThisChamber + "/" + targetCount + " into " + coordStr(c));
            if (thrownThisChamber >= targetCount) { startRetreat(); }
            else { clickSent = false; throwRetries = 0; spacingWait = cfg.throwSpacingTicks; }
            return;
        }
        if (throwWait > cfg.throwTimeoutTicks) {
            throwRetries++;
            if (throwRetries > cfg.maxThrowRetries) {
                if (thrownThisChamber > 0) finishChamberPartial("a throw wouldn't confirm");
                else abortOverhang("throw failed at " + coordStr(c));
            } else {
                clickSent = false; // retry the click
            }
        }
    }

    private void startRetreat() {
        phase = Phase.RETREAT;
        phaseTicks = 0;
    }

    private void tickRetreat() {
        var cfg = CONFIG.client.extra.pearlDrop;
        double x = playerX(), z = playerZ();
        StasisChamber c = chamber();
        float yaw = yawToColumn(x, z, c); // still facing the column; pressingBack moves away from it
        double hd = horizDist(x, z, c.columnX() + 0.5, c.columnZ() + 0.5);
        boolean onRim = (MathHelper.floorI(x) == rim.x() && MathHelper.floorI(z) == rim.z()) || hd >= cfg.retreatDistance;
        if (onRim || phaseTicks > cfg.retreatTimeoutTicks) {
            submitRelease();
            totalChambers++;
            phase = Phase.NEXT;
            phaseTicks = 0;
            return;
        }
        submitMove(false, true, true, false, yaw, 0f); // back + sneak: step safely off the ledge
    }

    private void finish() {
        phase = Phase.DONE;
        BARITONE.stop();
        submitRelease();
        if (totalThrown > 0) {
            result = totalFailed > 0 ? DepositResult.PARTIAL : DepositResult.SUCCESS;
        } else if (result == DepositResult.RUNNING) {
            result = DepositResult.ABORTED;
        }
        var embed = Embed.builder()
            .title("PearlDrop done")
            .addField("Pearls thrown", String.valueOf(totalThrown), true)
            .addField("Chambers filled", String.valueOf(totalChambers), true)
            .addField("Skipped/failed", String.valueOf(totalFailed), true)
            .addField("Result", result.name(), false);
        if (result == DepositResult.SUCCESS) embed.successColor();
        else if (totalThrown > 0) embed.primaryColor();
        else embed.errorColor();
        discordAndIngameNotification(embed);
    }

    /** A failure during the manual overhang/throw — back onto the rim, then move on. */
    private void abortOverhang(String why) {
        totalFailed++;
        info("PearlDrop: " + why);
        startRetreat();
    }

    /** Some pearls landed but the chamber couldn't be finished — retreat and continue. */
    private void finishChamberPartial(String why) {
        info("PearlDrop: " + why + " at " + coordStr(chamber()) + " (" + thrownThisChamber + " thrown)");
        startRetreat();
    }

    /** A setup failure before overhanging — no retreat needed, go straight to the next chamber. */
    private void skipChamber(DepositResult res, String why) {
        result = res;
        info("PearlDrop: " + why);
        BARITONE.stop();
        phase = Phase.NEXT;
        phaseTicks = 0;
    }

    // ------------------------------------------------------------------ chamber detection

    /**
     * All valid chambers anchored on soul sand within {@code rxz} horizontally and the y window
     * [cy - yDown, cy + yUp]; loaded chunks only. The window is asymmetric so a targeted search whose coords
     * point at the TOP of a chamber still reaches the soul sand several blocks below.
     */
    private List<StasisChamber> chambersInBox(double cx, double cy, double cz, int rxz, int yUp, int yDown) {
        List<StasisChamber> out = new ArrayList<>();
        int bx = MathHelper.floorI(cx), by = MathHelper.floorI(cy), bz = MathHelper.floorI(cz);
        for (int x = bx - rxz; x <= bx + rxz; x++) {
            for (int z = bz - rxz; z <= bz + rxz; z++) {
                if (!World.isChunkLoadedChunkPos(x >> 4, z >> 4)) continue;
                for (int y = by - yDown; y <= by + yUp; y++) {
                    if (World.getBlock(x, y, z) == BlockRegistry.SOUL_SAND) {
                        StasisChamber c = validateColumn(x, y, z);
                        if (c != null) out.add(c);
                    }
                }
            }
        }
        return out;
    }

    /** Validate a soul-sand block as the bottom of a stasis chamber. Returns the chamber or null. */
    private StasisChamber validateColumn(int sx, int sy, int sz) {
        var cfg = CONFIG.client.extra.pearlDrop;
        int k = 0;
        int y = sy + 1;
        while (World.isWater(World.getBlock(sx, y, sz))) { // water source OR bubble column
            k++;
            y++;
            if (k > 64) break;
        }
        Block cap = World.getBlock(sx, y, sz); // first non-water cell above the run
        if (!cap.blockTags().contains(BlockTags.TRAPDOORS)) return null; // must be capped by a trapdoor
        int stateId = World.getBlockStateId(sx, y, sz);
        Boolean wl = World.getBlockStateProperty(stateId, BlockStateProperties.WATERLOGGED);
        Boolean op = World.getBlockStateProperty(stateId, BlockStateProperties.OPEN);
        boolean waterlogged = wl != null && wl;
        boolean open = op != null && op;
        int depth = k + (waterlogged ? 1 : 0); // a waterlogged-trapdoor top cell counts toward the column depth
        if (depth < cfg.minWaterDepth) return null;
        return new StasisChamber(sx, sz, sy, y, depth, open, waterlogged);
    }

    /** Is an ender-pearl entity currently held inside this column? */
    private boolean occupied(StasisChamber c) {
        if (CACHE == null || CACHE.getEntityCache() == null) return false;
        for (var e : CACHE.getEntityCache().getEntities().values()) {
            if (e.getEntityType() != EntityType.ENDER_PEARL) continue;
            if (MathHelper.floorI(e.getX()) == c.columnX() && MathHelper.floorI(e.getZ()) == c.columnZ()
                && e.getY() >= c.soulSandY() - 0.5 && e.getY() <= c.trapdoorY() + 1.5) {
                return true;
            }
        }
        return false;
    }

    /** A standable block adjacent to the column to sneak-overhang from, nearest to the bot, or null. */
    private BlockPos findRim(StasisChamber c) {
        BlockPos best = null;
        long bestDsq = Long.MAX_VALUE;
        // prefer feet level with the trapdoor cell, then one above
        for (int feetY : new int[]{c.trapdoorY(), c.trapdoorY() + 1}) {
            for (int[] n : NEIGHBORS) {
                int nx = c.columnX() + n[0];
                int nz = c.columnZ() + n[1];
                boolean support = World.getBlock(nx, feetY - 1, nz).solidBlock();
                boolean clear = World.getBlock(nx, feetY, nz).isAir() && World.getBlock(nx, feetY + 1, nz).isAir();
                if (support && clear) {
                    long d = distSq(nx + 0.5, feetY, nz + 0.5, playerX(), playerY(), playerZ());
                    if (d < bestDsq) { bestDsq = d; best = new BlockPos(nx, feetY, nz); }
                }
            }
            if (best != null) return best; // take the lowest valid standing level
        }
        return best;
    }

    // ------------------------------------------------------------------ aiming / inventory helpers

    private boolean atRim() {
        return MathHelper.floorI(playerX()) == rim.x()
            && MathHelper.floorI(playerZ()) == rim.z()
            && MathHelper.floorI(playerY()) == rim.y();
    }

    private boolean fell(double y) {
        return y < rimFeetY - 0.4;
    }

    private float yawToColumn(double x, double z, StasisChamber c) {
        double dx = (c.columnX() + 0.5) - x;
        double dz = (c.columnZ() + 0.5) - z;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private float pitchToSoulSand(double x, double y, double z, StasisChamber c) {
        double eyeY = y + CONFIG.client.extra.pearlDrop.eyeHeight;
        double dx = (c.columnX() + 0.5) - x;
        double dy = (c.soulSandY() + 0.5) - eyeY;
        double dz = (c.columnZ() + 0.5) - z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        return (float) (-Math.toDegrees(Math.atan2(dy, horiz))); // dy < 0 (below eye) -> positive pitch (nose down)
    }

    private PearlState ensurePearlHeld() {
        var pc = CACHE.getPlayerCache();
        int hot = findHotbarPearl();
        if (hot < 0) {
            int m = findMainPearl();
            if (m < 0) return PearlState.NONE;
            if (INVENTORY.hasActiveRequest()) return PearlState.NOT_READY;
            int button = findEmptyHotbarButton();
            if (button < 0) button = pc.getHeldItemSlot();
            submitInv(new MoveToHotbarSlot(m, MoveToHotbarAction.from(button)));
            return PearlState.NOT_READY;
        }
        int idx = hot - 36;
        if (idx == pc.getHeldItemSlot()) return PearlState.READY;
        if (INVENTORY.hasActiveRequest()) return PearlState.NOT_READY;
        submitInv(new SetHeldItem(idx));
        return PearlState.NOT_READY;
    }

    private int findHotbarPearl() {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int s = 36; s <= 44; s++) if (isEnderPearl(inv.get(s))) return s;
        return -1;
    }

    private int findMainPearl() {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int s = 9; s <= 35; s++) if (isEnderPearl(inv.get(s))) return s;
        return -1;
    }

    private int findEmptyHotbarButton() {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int s = 36; s <= 44; s++) if (isEmpty(inv.get(s))) return s - 36;
        return -1;
    }

    private int countPearls() {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        int n = 0;
        for (int s = 9; s <= 44; s++) {
            var st = inv.get(s);
            if (isEnderPearl(st)) n += st.getAmount();
        }
        return n;
    }

    private boolean isEnderPearl(ItemStack s) {
        if (isEmpty(s)) return false;
        return ItemRegistry.REGISTRY.get(s.getId()) == ItemRegistry.ENDER_PEARL;
    }

    private static boolean isEmpty(ItemStack s) {
        return s == null || s == Container.EMPTY_STACK;
    }

    // ------------------------------------------------------------------ input plumbing

    private void submitMove(boolean forward, boolean back, boolean sneak, boolean click, float yaw, float pitch) {
        INPUTS.submit(InputRequest.builder()
            .owner(this)
            .input(Input.builder()
                .pressingForward(forward)
                .pressingBack(back)
                .sneaking(sneak)
                .sprinting(false)
                .rightClick(click)
                .hand(Hand.MAIN_HAND)
                .clickTarget(ClickTarget.None.INSTANCE)
                .clickRequiresRotation(false)
                .build())
            .yaw(yaw)
            .pitch(pitch)
            .priority(CONFIG.client.extra.pearlDrop.inputPriority)
            .build());
    }

    private void submitRelease() {
        var pc = CACHE.getPlayerCache();
        submitMove(false, false, false, false, pc.getYaw(), pc.getPitch());
    }

    private void submitInv(InventoryAction... actions) {
        INVENTORY.submit(InventoryActionRequest.builder()
            .owner(this)
            .actions(actions)
            .priority(CONFIG.client.extra.pearlDrop.inputPriority)
            .build());
    }

    // ------------------------------------------------------------------ misc helpers

    private static double playerX() { return CACHE.getPlayerCache().getX(); }
    private static double playerY() { return CACHE.getPlayerCache().getY(); }
    private static double playerZ() { return CACHE.getPlayerCache().getZ(); }

    private static double horizDist(double x, double z, double tx, double tz) {
        return Math.hypot(tx - x, tz - z);
    }

    private static long distSq(double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        return (long) (dx * dx + dy * dy + dz * dz);
    }

    /** Human-readable description of a chamber for listings. */
    public String describe(StasisChamber c) {
        return coordStr(c)
            + " depth " + c.depth()
            + (occupied(c) ? " OCCUPIED" : " empty")
            + (findRim(c) == null ? " (no rim)" : "")
            + (c.trapdoorOpen() ? " trapdoor-OPEN" : "");
    }

    private static String coordStr(StasisChamber c) {
        return "[" + c.columnX() + ", " + c.trapdoorY() + ", " + c.columnZ() + "]";
    }
}
