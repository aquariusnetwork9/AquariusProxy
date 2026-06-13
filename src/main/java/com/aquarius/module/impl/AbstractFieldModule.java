package com.aquarius.module.impl;

import com.aquarius.cache.data.inventory.Container;
import com.aquarius.feature.inventory.InventoryActionRequest;
import com.aquarius.feature.inventory.actions.ClickItem;
import com.aquarius.feature.inventory.actions.CloseContainer;
import com.aquarius.feature.inventory.actions.MoveToHotbarSlot;
import com.aquarius.feature.inventory.actions.SetHeldItem;
import com.aquarius.feature.inventory.actions.ShiftClick;
import com.aquarius.feature.inventory.util.InventoryUtil;
import com.aquarius.feature.pathfinder.goals.GoalNear;
import com.aquarius.feature.player.World;
import com.aquarius.mc.block.Block;
import com.aquarius.mc.block.BlockPos;
import com.aquarius.mc.block.Direction;
import com.aquarius.mc.item.ItemData;
import com.aquarius.mc.item.ItemRegistry;
import com.aquarius.module.api.Module;
import com.aquarius.util.math.MathHelper;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static com.aquarius.Globals.BARITONE;
import static com.aquarius.Globals.BOT;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.INVENTORY;

/**
 * Shared scaffolding for the field-automation modules ({@link Regear}, {@link KitMaker}) that drive a
 * place / open / shift-items / close / break / pickup container cycle on 2b2t. The primitives here are a
 * distilled, generic copy of the ones that {@link AquariusMiner} stabilised the hard way (drops fly up to
 * two blocks, close packets get dropped on lag, the server silently rejects a place under load, …). The
 * miner keeps its own private copies; this base is deliberately a separate, lighter implementation so the
 * miner's battle-tested code is never touched.
 *
 * <p>Subclasses own their own tick loop and state machine; this class only provides reusable building
 * blocks: container actions, a precise-count placer, placement-spot + open-ground finders, a world block
 * scan, coordinate pathing, item predicates, and the {@link #setBreakingAllowed(boolean)} gate that lets
 * KitMaker forbid block-breaking outside the shulker-harvest phase.
 */
public abstract class AbstractFieldModule extends Module {
    protected static final int ACTION_PRIORITY = 3000;
    /** GoalNear range² for "at" a container — within ~3 blocks, vanilla interaction reach (mirrors the miner). */
    protected static final int REACH_RANGE_SQ = 9;

    // breaking gate (KitMaker forbids breaking outside BREAK_SHULKER) -------------------------------------
    private boolean breakOverridden = false;
    private boolean breakSaved;

    /** Force pathfinder.allowBreak on/off, remembering the original so {@link #restoreBreaking()} can undo it. */
    protected void setBreakingAllowed(boolean allowed) {
        if (!breakOverridden) { breakSaved = CONFIG.client.extra.pathfinder.allowBreak; breakOverridden = true; }
        CONFIG.client.extra.pathfinder.allowBreak = allowed;
    }

    /** Restore pathfinder.allowBreak to whatever it was before the first {@link #setBreakingAllowed}. */
    protected void restoreBreaking() {
        if (breakOverridden) { CONFIG.client.extra.pathfinder.allowBreak = breakSaved; breakOverridden = false; }
    }

    // placing gate (the stash modules forbid placing entirely) -------------------------------------------
    private boolean placeOverridden = false;
    private boolean placeSaved;

    /** Force pathfinder.allowPlace on/off, remembering the original so {@link #restorePlacing()} can undo it. */
    protected void setPlacingAllowed(boolean allowed) {
        if (!placeOverridden) { placeSaved = CONFIG.client.extra.pathfinder.allowPlace; placeOverridden = true; }
        CONFIG.client.extra.pathfinder.allowPlace = allowed;
    }

    /** Restore pathfinder.allowPlace to whatever it was before the first {@link #setPlacingAllowed}. */
    protected void restorePlacing() {
        if (placeOverridden) { CONFIG.client.extra.pathfinder.allowPlace = placeSaved; placeOverridden = false; }
    }

    // ---------------------------------------------------------------- tick gating helpers

    /** True when the bot can't act yet this tick: dead, or its own chunk isn't loaded (just (re)connected). */
    protected boolean notReady() {
        if (!CACHE.getPlayerCache().isAlive()) return true;
        int cx = (int) Math.floor(CACHE.getPlayerCache().getX()) >> 4;
        int cz = (int) Math.floor(CACHE.getPlayerCache().getZ()) >> 4;
        return !World.isChunkLoadedChunkPos(cx, cz);
    }

    /** True if a non-self player is within {@code range} blocks (used for the optional safety soft-pause). */
    protected boolean playerNearby(double range) {
        double rsq = range * range;
        double px = CACHE.getPlayerCache().getX(), py = CACHE.getPlayerCache().getY(), pz = CACHE.getPlayerCache().getZ();
        long selfId = CACHE.getPlayerCache().getEntityId();
        for (var e : CACHE.getEntityCache().getEntities().values()) {
            if (e == null || !(e instanceof com.aquarius.cache.data.entity.EntityPlayer)) continue;
            if (e.getEntityId() == selfId) continue;
            double dx = e.getX() - px, dy = e.getY() - py, dz = e.getZ() - pz;
            if (dx * dx + dy * dy + dz * dz <= rsq) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- container / world primitives

    protected void place(@Nullable BlockPos pos, @Nullable ItemData item) {
        if (pos != null && item != null) BARITONE.placeBlock(pos.x(), pos.y(), pos.z(), item);
    }
    protected void open(@Nullable BlockPos pos) {
        if (pos != null) BARITONE.rightClickBlock(pos.x(), pos.y(), pos.z());
    }
    /**
     * Ghost-hand open: send the use-on-block packet directly (no Baritone path/raytrace), clicking the face that
     * points toward the bot. Lets the bot open a container it can't get a clean sightline to (walled in). The
     * caller must already be within a sane reach ({@code regear.ghostReach}); the server bounds it at ~6 blocks.
     */
    protected void openGhost(@Nullable BlockPos pos) {
        if (pos == null) return;
        BOT.getInteractions().ghostUseItemOn(pos.x(), pos.y(), pos.z(), faceTowardBot(pos), Hand.MAIN_HAND);
    }
    /** 3D distance from the bot to a block's centre. */
    protected double distToBot(BlockPos p) {
        return MathHelper.distance3d(
            CACHE.getPlayerCache().getX(), CACHE.getPlayerCache().getY(), CACHE.getPlayerCache().getZ(),
            p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
    }
    /** The block face pointing toward the bot (dominant axis) — a natural face to claim for a ghost click. */
    private Direction faceTowardBot(BlockPos p) {
        double dx = CACHE.getPlayerCache().getX() - (p.x() + 0.5);
        double dy = (CACHE.getPlayerCache().getY() + 1.0) - (p.y() + 0.5);
        double dz = CACHE.getPlayerCache().getZ() - (p.z() + 0.5);
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        if (ay >= ax && ay >= az) return dy >= 0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }
    protected void breakAt(@Nullable BlockPos pos, boolean autoTool) {
        if (pos != null) BARITONE.breakBlock(pos.x(), pos.y(), pos.z(), autoTool);
    }
    protected void closeContainer() {
        INVENTORY.submit(InventoryActionRequest.builder().owner(this).actions(new CloseContainer()).priority(ACTION_PRIORITY).build());
    }
    protected void shiftClick(Container c, int slot) {
        INVENTORY.submit(InventoryActionRequest.builder().owner(this)
            .actions(new ShiftClick(c.getContainerId(), slot, ShiftClickItemAction.LEFT_CLICK)).priority(ACTION_PRIORITY).build());
    }
    /** Left-click a slot in the open container window (pick up / drop the whole cursor). */
    protected void leftClick(Container c, int slot) {
        INVENTORY.submit(InventoryActionRequest.builder().owner(this)
            .actions(new ClickItem(c.getContainerId(), slot, ClickItemAction.LEFT_CLICK)).priority(ACTION_PRIORITY).build());
    }
    /** Right-click a slot in the open container window (deposit one from the cursor). */
    protected void rightClick(Container c, int slot) {
        INVENTORY.submit(InventoryActionRequest.builder().owner(this)
            .actions(new ClickItem(c.getContainerId(), slot, ClickItemAction.RIGHT_CLICK)).priority(ACTION_PRIORITY).build());
    }
    /** Select a player-inventory slot (9-44) as the held hotbar item (one swap if it's in the main inv). */
    protected void holdItemAt(int slot) {
        if (slot >= 36 && slot <= 44) {
            INVENTORY.submit(InventoryActionRequest.builder().owner(this)
                .actions(new SetHeldItem(slot - 36)).priority(ACTION_PRIORITY).build());
        } else if (slot >= 9 && slot <= 35) {
            INVENTORY.submit(InventoryActionRequest.builder().owner(this)
                .actions(new MoveToHotbarSlot(slot, MoveToHotbarAction.from(0)), new SetHeldItem(0)).priority(ACTION_PRIORITY).build());
        }
    }
    /** Move the item at player-inventory slot {@code slot} (9-44) into the offhand. */
    protected void moveToOffhand(int slot) {
        INVENTORY.submit(InventoryActionRequest.builder().owner(this)
            .actions(new MoveToHotbarSlot(slot, MoveToHotbarAction.OFF_HAND)).priority(ACTION_PRIORITY).build());
    }

    protected boolean placed(@Nullable BlockPos p) { return p != null && !World.getBlock(p.x(), p.y(), p.z()).isAir(); }
    protected boolean isAir(@Nullable BlockPos p) { return p != null && World.getBlock(p.x(), p.y(), p.z()).isAir(); }

    protected int openContainerId() { return CACHE.getPlayerCache().getInventoryCache().getOpenContainerId(); }
    protected @Nullable Container openContainer() { return CACHE.getPlayerCache().getInventoryCache().getOpenContainer(); }
    protected @Nullable ItemStack mouseStack() { return CACHE.getPlayerCache().getInventoryCache().getMouseStack(); }
    protected boolean cursorEmpty() { return mouseStack() == Container.EMPTY_STACK; }
    protected boolean inventoryBusy() { return INVENTORY.hasActiveRequest(); }

    // ---------------------------------------------------------------- slot finders

    /** First CONTAINER-half slot (0 .. size-37) matching {@code pred}, or -1. */
    protected int findContainerSlot(Container c, Predicate<ItemStack> pred) {
        int chestSlots = Math.max(0, c.getSize() - 36);
        for (int i = 0; i < chestSlots; i++) if (pred.test(c.getItemStack(i))) return i;
        return -1;
    }
    /** First PLAYER-half slot (size-36 .. size-1) of the open window matching {@code pred}, or -1. */
    protected int findPlayerWindowSlot(Container c, Predicate<ItemStack> pred) {
        int size = c.getSize();
        for (int i = Math.max(0, size - 36); i < size; i++) if (pred.test(c.getItemStack(i))) return i;
        return -1;
    }
    /** First empty PLAYER-half slot of the open window, or -1 (used to stash a leftover cursor stack). */
    protected int findEmptyPlayerWindowSlot(Container c) {
        return findPlayerWindowSlot(c, s -> s == Container.EMPTY_STACK);
    }
    /** First standalone player-inventory slot (9-44) matching {@code pred}, or -1. */
    protected int findInInv(Predicate<ItemStack> pred) {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int i = 9; i <= 44; i++) if (pred.test(inv.get(i))) return i;
        return -1;
    }
    /** Count standalone player-inventory slots (9-44) matching {@code pred}. */
    protected int countInInv(Predicate<ItemStack> pred) {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        int n = 0;
        for (int i = 9; i <= 44; i++) if (pred.test(inv.get(i))) n++;
        return n;
    }
    /** Empty standalone main-inventory slots (9-35) — the room gathered kit material may land in. */
    protected int emptyMainSlots() {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        int n = 0;
        for (int i = 9; i <= 35; i++) if (inv.get(i) == Container.EMPTY_STACK) n++;
        return n;
    }
    protected @Nullable ItemData itemDataAt(int slot) {
        ItemStack s = CACHE.getPlayerCache().getPlayerInventory().get(slot);
        return s == Container.EMPTY_STACK ? null : ItemRegistry.REGISTRY.get(s.getId());
    }

    // ---------------------------------------------------------------- item predicates

    protected @Nullable String itemName(@Nullable ItemStack s) {
        if (s == null || s == Container.EMPTY_STACK) return null;
        ItemData d = ItemRegistry.REGISTRY.get(s.getId());
        return d == null ? null : d.name();
    }
    protected boolean matchesName(@Nullable ItemStack s, String name) {
        String n = itemName(s);
        return n != null && n.equals(name);
    }
    /** Items inside a container item (shulker) via its CONTAINER component — positional: list index = slot,
     *  null for an empty slot, trailing empties trimmed. Empty list if the item carries no container. */
    protected List<ItemStack> containerContents(@Nullable ItemStack s) {
        if (s == null || s == Container.EMPTY_STACK) return List.of();
        List<ItemStack> c = s.getDataComponentsOrEmpty().get(DataComponentTypes.CONTAINER);
        return c == null ? List.of() : c;
    }
    /** The custom display name (plain text) of an item, or null if it has none. */
    protected @Nullable String customName(@Nullable ItemStack s) {
        if (s == null || s == Container.EMPTY_STACK) return null;
        var comp = s.getDataComponentsOrEmpty().get(DataComponentTypes.CUSTOM_NAME);
        return comp == null ? null : com.aquarius.util.ComponentSerializer.serializePlain(comp);
    }
    protected boolean isShulkerBox(@Nullable ItemStack s) {
        String n = itemName(s);
        return n != null && n.endsWith("shulker_box");
    }
    protected boolean isEmptyShulker(@Nullable ItemStack s) { return isShulkerBox(s) && contentsAllEmpty(containerContents(s)); }
    protected boolean isFilledShulker(@Nullable ItemStack s) { return isShulkerBox(s) && !contentsAllEmpty(containerContents(s)); }
    private boolean contentsAllEmpty(List<ItemStack> c) {
        for (ItemStack i : c) if (i != Container.EMPTY_STACK) return false;
        return true;
    }
    /** The configured ender-chest item that the miner / regear places as a buffer. */
    protected boolean isEnderChestItem(@Nullable ItemStack s) { return matchesName(s, "ender_chest"); }
    protected boolean hasEnderChest() { return InventoryUtil.searchPlayerInventory(this::isEnderChestItem) != -1; }

    // ---------------------------------------------------------------- placement-spot finders

    /** A clear air cell beside the bot, on solid non-fluid floor, that neither the bot's own body nor a
     *  cached entity occupies (those make a place fail with "entity blocking the place position"). Null if
     *  none. {@code avoid} skips a cell a place just failed at. Mirrors AquariusMiner.selectStorageSpot. */
    /**
     * Pick an empty cell beside the bot to place a shulker/echest. A shulker attaches to ANY face of a
     * neighbouring block (Baritone's {@code placeBlock} picks a reachable face), so don't demand a flat floor —
     * that was failing on uneven spawn terrain (walls but no floor-neighbour). Two passes: (1) prefer a clean
     * flat spot — solid floor with air above, so the lid opens upward; (2) fall back to ANY reachable air cell
     * that has at least one solid neighbour to attach to (wall, ceiling, or floor), scanning a block lower too.
     */
    protected @Nullable BlockPos selectSpotBeside(@Nullable BlockPos avoid) {
        BlockPos pf = BARITONE.getPlayerContext().playerFeet();
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        // Pass 1 — preferred: flat floor + air above (shulker sits on the ground and opens cleanly upward).
        for (int dy = 0; dy <= 1; dy++) {
            for (int[] d : dirs) {
                BlockPos cand = pf.add(d[0], dy, d[1]);
                if (!placeCellOpen(cand, pf, avoid)) continue;
                Block floor = World.getBlock(cand.x(), cand.y() - 1, cand.z());
                if (floor.isAir() || World.isFluid(floor)) continue;
                if (!World.getBlock(cand.x(), cand.y() + 1, cand.z()).isAir()) continue; // lid clearance
                return cand;
            }
        }
        // Pass 2 — fallback: any open cell with a face to attach to (wall/ceiling/floor); also one block lower.
        for (int dy = -1; dy <= 1; dy++) {
            for (int[] d : dirs) {
                BlockPos cand = pf.add(d[0], dy, d[1]);
                if (!placeCellOpen(cand, pf, avoid)) continue;
                if (hasSolidNeighbor(cand)) return cand;
            }
        }
        return null;
    }

    /** The cell is empty air, not the bot's own footprint, not the avoid spot, and not occupied by an entity. */
    private boolean placeCellOpen(BlockPos cand, BlockPos pf, @Nullable BlockPos avoid) {
        if (cand.equals(pf) || cand.equals(pf.above())) return false;
        if (avoid != null && cand.equals(avoid)) return false;
        if (!World.getBlock(cand.x(), cand.y(), cand.z()).isAir()) return false;
        if (playerBoxIntersects(cand)) return false;
        return !entityOccupies(cand);
    }

    /** Any of the 6 neighbouring blocks is solid (non-air, non-fluid) — a face the shulker can attach to. */
    private boolean hasSolidNeighbor(BlockPos c) {
        int[][] faces = {{0, -1, 0}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] f : faces) {
            Block b = World.getBlock(c.x() + f[0], c.y() + f[1], c.z() + f[2]);
            if (!b.isAir() && !World.isFluid(b)) return true;
        }
        return false;
    }

    private boolean entityOccupies(BlockPos p) {
        for (var e : CACHE.getEntityCache().getEntities().values()) {
            if (e == null) continue;
            if ((int) Math.floor(e.getX()) != p.x() || (int) Math.floor(e.getZ()) != p.z()) continue;
            double feetY = e.getY();
            if (feetY < p.y() + 1 && feetY + 2.0 > p.y()) return true;
        }
        return false;
    }

    private boolean playerBoxIntersects(BlockPos p) {
        double px = CACHE.getPlayerCache().getX(), py = CACHE.getPlayerCache().getY(), pz = CACHE.getPlayerCache().getZ();
        double half = 0.32, height = 1.8;
        boolean xo = p.x() < px + half && p.x() + 1 > px - half;
        boolean zo = p.z() < pz + half && p.z() + 1 > pz - half;
        boolean yo = p.y() < py + height && p.y() + 1 > py;
        return xo && yo && zo;
    }

    // ---------------------------------------------------------------- coordinate pathing

    /** Begin pathing to within interaction reach of a block. Returns the goal; poll {@link #arrivedAt}. */
    protected GoalNear pathToNear(BlockPos pos) {
        GoalNear goal = new GoalNear(pos.x(), pos.y(), pos.z(), REACH_RANGE_SQ);
        BARITONE.pathTo(goal);
        return goal;
    }
    protected boolean arrivedAt(@Nullable GoalNear goal) {
        if (goal == null) return true;
        BlockPos feet = BARITONE.getPlayerContext().playerFeet();
        return goal.isInGoal(feet.x(), feet.y(), feet.z());
    }
    protected BlockPos playerFeet() { return BARITONE.getPlayerContext().playerFeet(); }

    // ---------------------------------------------------------------- world block scan

    /** Nearest loaded block within a horizontal {@code radius} and Y in [yLo, yHi] whose name passes
     *  {@code nameTest}, by squared distance from the bot's feet. Null if none. */
    protected @Nullable BlockPos nearestBlock(int radius, int yLo, int yHi, Predicate<String> nameTest) {
        BlockPos pf = playerFeet();
        BlockPos best = null;
        long bestD = Long.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = pf.x() + dx, z = pf.z() + dz;
                if (dx * dx + dz * dz > radius * radius) continue;
                if (!World.isChunkLoadedBlockPos(x, z)) continue;
                for (int y = yLo; y <= yHi; y++) {
                    Block b = World.getBlock(x, y, z);
                    if (b.isAir() || !nameTest.test(b.name())) continue;
                    long d = (long) dx * dx + (long) dz * dz + (long) (y - pf.y()) * (y - pf.y());
                    if (d < bestD) { bestD = d; best = new BlockPos(x, y, z); }
                }
            }
        }
        return best;
    }

    /** Every loaded block within a horizontal {@code radius} and Y in [yLo, yHi] whose name passes
     *  {@code nameTest}. Used for the KitMaker floor-level container discovery. */
    protected List<BlockPos> findBlocks(int radius, int yLo, int yHi, Predicate<String> nameTest) {
        BlockPos pf = playerFeet();
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = pf.x() + dx, z = pf.z() + dz;
                if (dx * dx + dz * dz > radius * radius) continue;
                if (!World.isChunkLoadedBlockPos(x, z)) continue;
                for (int y = yLo; y <= yHi; y++) {
                    Block b = World.getBlock(x, y, z);
                    if (!b.isAir() && nameTest.test(b.name())) out.add(new BlockPos(x, y, z));
                }
            }
        }
        return out;
    }

    /** A container block name we treat as a kit chest/barrel (NOT an ender chest). */
    protected boolean isContainerBlockName(String n) {
        return n.equals("chest") || n.equals("trapped_chest") || n.equals("barrel");
    }
}
