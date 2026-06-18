package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.inventory.InventoryActionRequest;
import com.aquarius.feature.inventory.actions.MoveToHotbarSlot;
import com.aquarius.feature.inventory.actions.SetHeldItem;
import com.aquarius.mc.block.Direction;
import com.aquarius.mc.item.ItemRegistry;
import com.aquarius.module.api.Module;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.BOT;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.INVENTORY;
import static com.aquarius.cache.data.inventory.Container.EMPTY_STACK;

/**
 * AutoPortal — build a nether portal in front of the bot and light it ({@code .portal build}). A consumer of the
 * AirPlace primitive ({@link com.aquarius.feature.player.PlayerInteractionManager#emitAirPlace}): the whole frame is
 * placed from where the bot stands (airplace removes the reach/LOS limit), so the bot never has to path between
 * blocks.
 *
 * <p>Builds the <b>minimal cornerless</b> frame (10 obsidian): a 4-wide x 5-tall outer rectangle minus the four
 * corners, interior 2x3. The frame plane is vertical, its width axis perpendicular to the bot's facing, base at the
 * bot's feet level, offset {@code buildDistance} blocks ahead. Three cells have no orthogonal neighbour to place
 * against (the two column-bottoms next to the missing bottom corners, and the first top-row block) — those rely on
 * true place-into-air; the rest place against a just-placed neighbour or the ground (deterministic). Then it lights
 * the inner-bottom obsidian's top face with flint &amp; steel (or a fire charge).
 *
 * <p>Stops after lighting (build + light only; walking through is left to a caller). Aborts cleanly if it lacks
 * &ge;10 obsidian or a lighter, or if the build exceeds {@code timeoutTicks}.
 */
public class AutoPortal extends Module {

    public enum State { IDLE, PLACING, LIGHTING, DONE, FAILED }

    /** One frame block: the cell to fill, plus the (target block + face) of the UseItemOn that fills it. */
    private record Step(int tx, int ty, int tz, Direction face, boolean floating) {}

    private State state = State.IDLE;
    private final List<Step> steps = new ArrayList<>();
    private int stepIndex;
    private int placeDelay;   // ticks until the next placement
    private int ticks;        // ticks since the build started (timeout guard)

    // inner-bottom obsidian to light (the bottom-left interior frame block's top face)
    private int lightX, lightY, lightZ;

    @Override
    public boolean enabledSetting() {
        return false; // command-driven (.portal build); never auto-enabled on startup
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(ClientBotTick.Stopped.class, e -> reset())
        );
    }

    @Override
    public void onDisable() {
        reset();
    }

    public State getState() { return state; }

    /** Cancel an in-progress build and disable the module. */
    public void cancel() {
        info("AutoPortal cancelled.");
        disable(); // -> onDisable -> reset
    }

    /**
     * Plan + start a portal build at the bot's current position/facing. Validates materials up front.
     * @return true if the build started; false (with a reason logged) if it can't.
     */
    public boolean start() {
        var pc = CACHE.getPlayerCache();
        if (pc.getThePlayer() == null || !pc.getThePlayer().isAlive()) {
            info("Can't build: no live player.");
            return false;
        }
        int obsidian = countItem(ItemRegistry.OBSIDIAN.id());
        if (obsidian < 10) {
            info("Can't build: need >=10 obsidian, have {}.", obsidian);
            return false;
        }
        int lighterId = lighterId();
        if (countItem(lighterId) == 0) {
            info("Can't build: no {} to light the portal.", CONFIG.client.extra.autoPortal.useFireCharge ? "fire charge" : "flint & steel");
            return false;
        }
        planSteps(pc.getX(), pc.getY(), pc.getZ(), pc.getYaw());
        stepIndex = 0;
        placeDelay = 0;
        ticks = 0;
        state = State.PLACING;
        enable();
        info("Building cornerless nether portal: {} obsidian frame, then light.", steps.size());
        return true;
    }

    // ---------------------------------------------------------------- geometry

    private void planSteps(double px, double py, double pz, float yaw) {
        steps.clear();
        final Direction facing = facingFromYaw(yaw);
        // width axis = perpendicular horizontal to facing
        final Direction w = (facing.getAxis() == Direction.Axis.Z) ? Direction.EAST : Direction.SOUTH;

        final int dist = Math.max(1, CONFIG.client.extra.autoPortal.buildDistance);
        // origin = column-0 / bottom-row block position, dist blocks ahead, at the bot's feet level
        final int ox = floor(px) + facing.x() * dist;
        final int oy = floor(py);                 // bottom frame row sits here (ground is at oy-1)
        final int oz = floor(pz) + facing.z() * dist;

        // cell(col,row) absolute coords; col 0..3 along width, row 0..4 vertical
        // Frame (cornerless): bottom row cols 1,2 (row 0); top row cols 1,2 (row 4); side columns 0 & 3 (rows 1..3).

        // bottom row — placed against the ground below (target = ground block, face = UP)
        for (int col = 1; col <= 2; col++) {
            int[] c = cell(ox, oy, oz, w, col, 0);
            steps.add(new Step(c[0], oy - 1, c[2], Direction.UP, false));
        }
        // side columns — bottom block floats (air-place the cell itself); the two above stack on it
        for (int colSide : new int[]{0, 3}) {
            int[] c1 = cell(ox, oy, oz, w, colSide, 1);
            steps.add(new Step(c1[0], c1[1], c1[2], Direction.DOWN, true)); // floating: target the cell, claim DOWN face
            int[] c2 = cell(ox, oy, oz, w, colSide, 2);
            steps.add(new Step(c1[0], c1[1], c1[2], Direction.UP, false)); // on top of row-1 block
            int[] c3 = cell(ox, oy, oz, w, colSide, 3);
            steps.add(new Step(c2[0], c2[1], c2[2], Direction.UP, false)); // on top of row-2 block
        }
        // top row — first block floats; the second hangs off it along the width axis
        int[] t1 = cell(ox, oy, oz, w, 1, 4);
        steps.add(new Step(t1[0], t1[1], t1[2], Direction.DOWN, true));     // floating
        steps.add(new Step(t1[0], t1[1], t1[2], w, false));                 // t2 against t1's width-face

        // light target: top face of the bottom-row obsidian at col 1 -> fire spawns in the interior bottom
        int[] b1 = cell(ox, oy, oz, w, 1, 0);
        lightX = b1[0]; lightY = b1[1]; lightZ = b1[2];
    }

    private static int[] cell(int ox, int oy, int oz, Direction w, int col, int row) {
        return new int[]{ ox + w.x() * col, oy + row, oz + w.z() * col };
    }

    /** Minecraft horizontal facing from yaw: 0=+Z(S), 90=-X(W), 180=-Z(N), 270=+X(E). */
    private static Direction facingFromYaw(float yaw) {
        int i = Math.floorMod(Math.round(yaw / 90f), 4);
        return switch (i) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    private static int floor(double d) { return (int) Math.floor(d); }

    // ---------------------------------------------------------------- tick / state machine

    private void onTick(ClientBotTick event) {
        if (state != State.PLACING && state != State.LIGHTING) return;
        if (++ticks > CONFIG.client.extra.autoPortal.timeoutTicks) {
            fail("timed out");
            return;
        }
        switch (state) {
            case PLACING -> tickPlacing();
            case LIGHTING -> tickLighting();
            default -> {}
        }
    }

    private void tickPlacing() {
        if (placeDelay > 0) { placeDelay--; return; }
        if (stepIndex >= steps.size()) { state = State.LIGHTING; return; }
        if (countItem(ItemRegistry.OBSIDIAN.id()) == 0) { fail("ran out of obsidian"); return; }
        if (!ensureHeld(ItemRegistry.OBSIDIAN.id())) return; // selecting / moving to hotbar — wait
        Step st = steps.get(stepIndex);
        boolean placed = BOT.getInteractions().emitAirPlace(st.tx(), st.ty(), st.tz(), st.face());
        if (placed) {                 // false = yielded to AutoTotem this tick; retry next tick
            stepIndex++;
            placeDelay = Math.max(1, CONFIG.client.extra.autoPortal.placeIntervalTicks);
        }
    }

    private void tickLighting() {
        int lighterId = lighterId();
        if (countItem(lighterId) == 0) { fail("no lighter for ignition"); return; }
        if (!ensureHeld(lighterId)) return;
        BOT.getInteractions().ghostUseItemOn(lightX, lightY, lightZ, Direction.UP, Hand.MAIN_HAND);
        info("Portal frame complete + lit.");
        state = State.DONE;
        disable();
    }

    private void fail(String why) {
        warn("AutoPortal failed: {} (placed {}/{}).", why, stepIndex, steps.size());
        state = State.FAILED;
        disable();
    }

    private void reset() {
        if (state == State.PLACING || state == State.LIGHTING) state = State.IDLE;
        steps.clear();
        stepIndex = 0;
        placeDelay = 0;
        ticks = 0;
    }

    // ---------------------------------------------------------------- inventory helpers

    private int lighterId() {
        return (CONFIG.client.extra.autoPortal.useFireCharge ? ItemRegistry.FIRE_CHARGE : ItemRegistry.FLINT_AND_STEEL).id();
    }

    /** True once {@code itemId} is in the main hand; otherwise kicks off a select/move and returns false. */
    private boolean ensureHeld(int itemId) {
        var pc = CACHE.getPlayerCache();
        var inv = pc.getPlayerInventory();
        int heldSlot = 36 + pc.getHeldItemSlot();
        if (is(inv.get(heldSlot), itemId)) return true;
        if (INVENTORY.hasActiveRequest()) return false; // let a previous action settle
        for (int s = 36; s <= 44; s++) {                // already in the hotbar — just select it
            if (is(inv.get(s), itemId)) { submit(new SetHeldItem(s - 36)); return false; }
        }
        for (int s = 9; s <= 35; s++) {                 // in the main inventory — pull it to a hotbar slot
            if (is(inv.get(s), itemId)) {
                int button = emptyHotbarButtonOrHeld();
                submit(new MoveToHotbarSlot(s, MoveToHotbarAction.from(button)), new SetHeldItem(button));
                return false;
            }
        }
        return false; // none anywhere (countItem guards against this before we get here)
    }

    private int emptyHotbarButtonOrHeld() {
        var inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int s = 36; s <= 44; s++) if (isEmpty(inv.get(s))) return s - 36;
        return CACHE.getPlayerCache().getHeldItemSlot();
    }

    private int countItem(int id) {
        int n = 0;
        for (ItemStack s : CACHE.getPlayerCache().getPlayerInventory()) if (is(s, id)) n += s.getAmount();
        return n;
    }

    private void submit(com.aquarius.feature.inventory.actions.InventoryAction... actions) {
        INVENTORY.submit(InventoryActionRequest.builder().owner(this).actions(actions).priority(3000).build());
    }

    private static boolean isEmpty(ItemStack s) { return s == null || s == EMPTY_STACK; }
    private static boolean is(ItemStack s, int id) { return !isEmpty(s) && s.getId() == id; }
}
