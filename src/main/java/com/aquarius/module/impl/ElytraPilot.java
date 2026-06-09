package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.cache.data.inventory.Container;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.inventory.InventoryActionRequest;
import com.aquarius.feature.inventory.actions.ClickItem;
import com.aquarius.feature.inventory.actions.InventoryAction;
import com.aquarius.feature.inventory.actions.MoveToHotbarSlot;
import com.aquarius.feature.inventory.actions.SetHeldItem;
import com.aquarius.feature.player.ClickTarget;
import com.aquarius.feature.player.Input;
import com.aquarius.feature.player.InputRequest;
import com.aquarius.feature.pathfinder.goals.GoalNear;
import com.aquarius.feature.player.World;
import com.aquarius.mc.item.ItemRegistry;
import com.aquarius.module.api.Module;
import com.aquarius.util.config.Config.Client.Extra.ElytraPilot.HighwayDir;
import com.aquarius.util.math.MathHelper;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;

import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.BARITONE;
import static com.aquarius.Globals.BOT;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.INPUTS;
import static com.aquarius.Globals.INVENTORY;

/**
 * ElytraPilot — autopilot elytra flight.
 *
 * <p>The executor ({@link com.aquarius.feature.player.Bot}) already simulates the full elytra physics: glide
 * aerodynamics in {@code travelFallFlying}, firework propulsion in {@code tickEntities} (the vanilla
 * {@code lookVec * 1.5} boost applied while a firework rocket is attached and the bot is fall-flying), and
 * auto-deploy when the jump input is set while airborne. This module is purely the GUIDANCE loop on top of
 * that: it takes off, steers by submitting a per-tick yaw/pitch to {@code INPUTS}, fires fireworks to sustain
 * speed, does crude terrain look-ahead, swaps in a fresh elytra when the worn one wears out, recovers from
 * any loss of flight, and lands near a target.
 *
 * <p>It does NOT touch the pathfinder (Baritone has no flight movement type), so flight is hand-flown toward
 * a coordinate or a fixed heading. Requires an elytra worn in the chestplate slot and firework rockets in the
 * hotbar; for long hauls, keep spare (high-durability) elytras in the hotbar/inventory. All settings live
 * under {@code CONFIG.client.extra.elytraPilot}.
 */
public class ElytraPilot extends Module {

    private enum Phase { IDLE, TAKEOFF, CRUISE, BOUNCE, PASS, SWAP, DESCEND, LAND, EMERGENCY, DONE }

    private static final int CHESTPLATE_SLOT = 6;          // container 0: 5=helm,6=chest,7=legs,8=boots
    private static final int TAKEOFF_TIMEOUT_TICKS = 200;  // ~10s to get airborne + deployed
    private static final int LAND_TIMEOUT_TICKS = 200;
    private static final int SWAP_EQUIP_TIMEOUT_TICKS = 100;
    private static final int SWAP_REDEPLOY_TIMEOUT_TICKS = 100;

    private Phase phase = Phase.IDLE;
    private int flightTicks;
    private int takeoffTicks;
    private int landTicks;
    private int swapTicks;
    private int ticksSinceFire;
    private boolean jumpToggle;
    private boolean gliding;
    private boolean swapRedeploying;
    private int redeployCooldown;
    private int bounceStallTicks;
    private boolean passPathing;
    private int passTicks;
    private int passAttempts;
    private int passTX, passTZ;
    private boolean emergencyAlerted;
    private boolean noSpareWarned;
    private double lastX, lastZ;
    private boolean haveLast;

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.elytraPilot.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(ClientBotTick.Starting.class, e -> reset()),
            of(ClientBotTick.Stopped.class, e -> reset())
        );
    }

    @Override
    public void onEnable() {
        reset();
        phase = CONFIG.client.extra.elytraPilot.ebounce ? Phase.BOUNCE : Phase.TAKEOFF;
        BARITONE.stop();
        var cfg = CONFIG.client.extra.elytraPilot;
        if (!isElytra(chestplate()))
            inGameAlertActivePlayer("<yellow>ElytraPilot: no elytra in the chestplate slot — will still try to deploy");
        if (findHotbarFirework() < 0)
            inGameAlertActivePlayer("<yellow>ElytraPilot: no firework rockets in hotbar — flight will not sustain");
        if (cfg.swapElytra && !hasSpareElytra())
            inGameAlertActivePlayer("<yellow>ElytraPilot: no spare elytra found — long flights will end when the worn one wears out");
        if (cfg.hasTarget)
            info("Enabled: flying to " + cfg.targetX + ", " + cfg.targetZ
                + " (glide band " + cfg.glideFloorY + "-" + cfg.glideCeilingY + ")");
        else
            info("Enabled: free-flying heading " + cfg.heading
                + " (glide band " + cfg.glideFloorY + "-" + cfg.glideCeilingY + ")");
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        phase = Phase.IDLE;
        flightTicks = 0;
        takeoffTicks = 0;
        landTicks = 0;
        swapTicks = 0;
        ticksSinceFire = Integer.MAX_VALUE / 2;
        jumpToggle = false;
        gliding = false;
        swapRedeploying = false;
        redeployCooldown = 0;
        bounceStallTicks = 0;
        passPathing = false;
        passTicks = 0;
        passAttempts = 0;
        emergencyAlerted = false;
        noSpareWarned = false;
        haveLast = false;
    }

    private void onTick(final ClientBotTick event) {
        try {
            if (phase == Phase.IDLE || phase == Phase.DONE) return;
            var pc = CACHE.getPlayerCache();
            int cx = MathHelper.floorI(pc.getX()) >> 4;
            int cz = MathHelper.floorI(pc.getZ()) >> 4;
            if (!World.isChunkLoadedChunkPos(cx, cz)) return; // wait for the world to load

            var cfg = CONFIG.client.extra.elytraPilot;
            if (++flightTicks > cfg.maxFlightTicks) { abort("flight time limit reached"); return; }

            double x = pc.getX(), y = pc.getY(), z = pc.getZ();
            double speed = haveLast ? Math.hypot(x - lastX, z - lastZ) : 0.0;

            // Self-heal: if we should be gliding but aren't (elytra broke, desync, knockback), recover.
            if ((phase == Phase.CRUISE || phase == Phase.DESCEND) && !BOT.isFallFlying()) {
                handleLostFlight(x, y, z);
            } else {
                switch (phase) {
                    case TAKEOFF   -> tickTakeoff();
                    case CRUISE    -> tickCruise(x, y, z, speed);
                    case BOUNCE    -> tickBounce(x, y, z, speed);
                    case PASS      -> tickPass(x, y, z, speed);
                    case SWAP      -> tickSwap(x, y, z);
                    case DESCEND   -> tickDescend(x, y, z, speed);
                    case LAND      -> tickLand();
                    case EMERGENCY -> tickEmergency(x, y, z);
                    default        -> { }
                }
            }

            lastX = x;
            lastZ = z;
            haveLast = true;
            ticksSinceFire++;
            if (redeployCooldown > 0) redeployCooldown--;
        } catch (final Exception e) {
            error("Error in flight tick", e);
            abort("internal error");
        }
    }

    // --- phases ---

    private void tickTakeoff() {
        if (BOT.isFallFlying()) {
            phase = CONFIG.client.extra.elytraPilot.ebounce ? Phase.BOUNCE : Phase.CRUISE;
            ticksSinceFire = Integer.MAX_VALUE / 2; // fire immediately on the first cruise tick
            info("Airborne — entering " + (CONFIG.client.extra.elytraPilot.ebounce ? "bounce" : "cruise"));
            return;
        }
        var cfg = CONFIG.client.extra.elytraPilot;
        ensureFireworkHeld();
        boolean jump;
        if (cfg.doubleJumpTakeoff) {
            jump = jumpToggle;          // alternate press/release so a fresh jump edge lands while airborne
            jumpToggle = !jumpToggle;
        } else {
            jump = true;                // assume already airborne (ledge/tower) — just deploy
        }
        submitInput(jump, false, CACHE.getPlayerCache().getYaw(), 0f);
        if (++takeoffTicks > TAKEOFF_TIMEOUT_TICKS)
            abort("could not take off (need open sky above + a worn elytra)");
    }

    private void tickCruise(double x, double y, double z, double speed) {
        var cfg = CONFIG.client.extra.elytraPilot;
        float yaw = desiredYaw(x, z);

        // Elytra wear management.
        if (cfg.swapElytra && needsElytraSwap()) {
            if (hasSpareElytra()) {
                if (heightAboveGround(x, y, z) >= cfg.minSwapClearance) {
                    enterSwap();
                    return;
                }
                // too low to safely drop flight for a swap: climb first
                boolean fire = heldIsFirework();
                if (!fire) ensureFireworkHeld(); else ticksSinceFire = 0;
                submitInput(false, fire, yaw, -35f);
                return;
            } else {
                if (!noSpareWarned) {
                    inGameAlertActivePlayer("<yellow>ElytraPilot: elytra low, no spare — flying until it fails");
                    noSpareWarned = true;
                }
                if (wornElytraDurability() <= 2) { phase = Phase.EMERGENCY; return; }
            }
        }

        // Long-haul profile: firework-climb (nose up) to the ceiling, then glide (nose ≈ +2) to the floor.
        if (cfg.glideFloorY < cfg.glideCeilingY) {
            if (gliding && y <= cfg.glideFloorY) gliding = false;        // sank to the floor -> climb again
            else if (!gliding && y >= cfg.glideCeilingY) gliding = true; // reached the ceiling -> glide
        } else {
            gliding = y >= cfg.glideCeilingY;                           // degenerate band
        }
        boolean overCap = speed * 20.0 >= cfg.maxSpeed; // 2b2t ~40 b/s limit — never boost past it
        float pitch;
        boolean wantFire;
        if (gliding) {
            pitch = cfg.glidePitch;   // shallow nose-down glide = max distance per altitude; no fireworks
            wantFire = false;
        } else {
            pitch = -cfg.climbPitch;  // nose-up firework ascent = max height per firework
            wantFire = !overCap && (speed < cfg.minBoostSpeed || ticksSinceFire >= cfg.maxBoostIntervalTicks);
        }
        if (terrainBlockedAhead(x, y, z, yaw, cfg.lookAheadBlocks)) { // pull up + boost over obstacles
            pitch = -cfg.climbPitch;
            wantFire = !overCap;
        }
        boolean fire = wantFire && heldIsFirework();
        if (wantFire && !fire) ensureFireworkHeld();
        if (fire) ticksSinceFire = 0;
        submitInput(false, fire, yaw, pitch);

        // Begin the final descent early enough to glide down to the target (lead scales with altitude).
        if (cfg.hasTarget) {
            double lead = Math.max(cfg.descendRadius, (y - cfg.approxGroundY) * cfg.glideRatio);
            if (horizDist(x, z, cfg.targetX, cfg.targetZ) <= lead) {
                phase = Phase.DESCEND;
                info("Approaching target — descending");
            }
        }
    }

    /**
     * E-bounce: skip along a flat road with no fireworks. Holds forward+jump+sprint at +2° pitch and re-sends
     * START_FALL_FLYING each bounce. The held jump auto-jumps the instant the bot touches the road (vanilla
     * physics, with its own ~10-tick jump cooldown setting the bounce cadence), and the re-engaged elytra glide
     * carries the hop forward. Decoded from a real Rusherhack capture. Flat road only.
     */
    private void tickBounce(double x, double y, double z, double speed) {
        var cfg = CONFIG.client.extra.elytraPilot;
        float yaw = desiredYaw(x, z);

        // Road sanity: bounce only works on a flat road. If we've sunk well below it, we hit a gap / fell off.
        if (y < cfg.roadY - cfg.roadDropAbort) {
            abort("dropped below the road (y " + (int) y + " < road " + cfg.roadY + ")");
            return;
        }

        // Elytra wear is the same in bounce mode (fall-flying still drains durability); SWAP returns to BOUNCE.
        if (cfg.swapElytra && needsElytraSwap()) {
            if (hasSpareElytra()) { enterSwap(); return; }
            if (wornElytraDurability() <= 2 && !noSpareWarned) {
                inGameAlertActivePlayer("<yellow>ElytraPilot: elytra low, no spare");
                noSpareWarned = true;
            }
        }

        // Obstacle passing: a block ahead (or a forward-progress stall) hands off to Baritone to get past it.
        if (cfg.passObstacles) {
            if (speed * 20.0 < 2.0) bounceStallTicks++; else bounceStallTicks = 0;
            boolean blocked = terrainBlockedAhead(x, y, z, yaw, Math.min(cfg.lookAheadBlocks, 8));
            if (blocked || bounceStallTicks > cfg.bounceStallLimit) {
                enterPass();
                return;
            }
        }

        boolean overCap = speed * 20.0 >= cfg.maxSpeed; // 2b2t ~40 b/s limit — coast (no bounce) to bleed speed
        if (!BOT.isFallFlying() && !overCap && redeployCooldown <= 0) {
            sendStartFallFlying();                       // re-engage the elytra at the bottom of each bounce
            redeployCooldown = cfg.bounceRedeployTicks;
        }
        boolean jump = !overCap;                         // held jump auto-jumps on each ground touch
        submitMove(true, jump, true, false, yaw, cfg.glidePitch);

        if (cfg.hasTarget && horizDist(x, z, cfg.targetX, cfg.targetZ) <= cfg.arriveRadius) {
            phase = Phase.LAND;
            landTicks = 0;
            info("Reached target — landing");
        }
    }

    private void enterPass() {
        phase = Phase.PASS;
        passPathing = false;
        passTicks = 0;
        passAttempts = 0;
        bounceStallTicks = 0;
        BARITONE.stop();
        inGameAlertActivePlayer("<yellow>ElytraPilot: obstacle ahead — settling + pathing past it");
    }

    /**
     * Obstacle pass: stop the bounce and settle on the road, then let Baritone walk to a clear spot further along
     * the travel axis (re-centered on the highway line), and resume bouncing. Bounded retries, then aborts.
     */
    private void tickPass(double x, double y, double z, double speed) {
        var cfg = CONFIG.client.extra.elytraPilot;
        if (!passPathing) {
            // settle: kill the bounce inputs so the bot stops and lands, then hand off to Baritone
            submitMove(false, false, false, false, desiredYaw(x, z), 0f);
            boolean settled = speed * 20.0 < 1.5 && !BOT.isFallFlying();
            if (settled || ++passTicks > cfg.passSettleTicks) startPassPath(x, z);
            return;
        }
        // Baritone is driving now — do NOT submit inputs; just watch for arrival / path failure.
        if (horizDist(x, z, passTX, passTZ) <= 3 && Math.abs(y - cfg.roadY) <= 3) {
            BARITONE.stop();
            phase = Phase.BOUNCE;
            bounceStallTicks = 0;
            info("Cleared obstacle — resuming bounce");
            return;
        }
        if (!BARITONE.isActive() || ++passTicks > cfg.passTimeoutTicks) {
            if (passAttempts >= cfg.maxPassAttempts) { abort("could not path past the obstacle"); return; }
            startPassPath(x, z); // re-path further along the axis
        }
    }

    private void startPassPath(double x, double z) {
        var cfg = CONFIG.client.extra.elytraPilot;
        BARITONE.stop();
        passAttempts++;
        double[] d = travelUnit();
        double ahead = cfg.passAheadBlocks * passAttempts;
        double tx, tz;
        if (cfg.highway) {                 // re-center on the highway line, past the obstacle
            double t = x * d[0] + z * d[1];
            tx = (t + ahead) * d[0];
            tz = (t + ahead) * d[1];
        } else {                           // plain heading bounce: straight ahead from here
            tx = x + d[0] * ahead;
            tz = z + d[1] * ahead;
        }
        passTX = (int) Math.floor(tx);
        passTZ = (int) Math.floor(tz);
        BARITONE.pathTo(new GoalNear(passTX, cfg.roadY, passTZ, 9));
        passPathing = true;
        passTicks = 0;
    }

    /** Unit travel direction: the highway axis if in highway mode, else derived from the configured heading. */
    private double[] travelUnit() {
        var cfg = CONFIG.client.extra.elytraPilot;
        if (cfg.highway) return highwayUnit(cfg.highwayDir);
        double r = Math.toRadians(cfg.heading);
        return new double[]{ -Math.sin(r), Math.cos(r) };
    }

    /** Equip a fresh elytra mid-air, then re-deploy + re-boost. The bot free-falls while the chestplate is swapped. */
    private void tickSwap(double x, double y, double z) {
        var cfg = CONFIG.client.extra.elytraPilot;
        float yaw = desiredYaw(x, z);

        if (!swapRedeploying) {
            boolean done = equipFreshElytraProgress();
            submitInput(false, false, yaw, 0f); // no elytra / not gliding — just hold heading while we fall
            if (heightAboveGround(x, y, z) < cfg.minSwapClearance / 2) { phase = Phase.EMERGENCY; return; }
            if (done) {
                swapRedeploying = true;
                swapTicks = 0;
            } else if (++swapTicks > SWAP_EQUIP_TIMEOUT_TICKS) {
                if (!hasSpareElytra()) { phase = Phase.EMERGENCY; return; }
                swapTicks = 0; // keep retrying (bounded overall by maxFlightTicks)
            }
            return;
        }

        // Re-deploy the freshly equipped elytra, then boost back up to speed.
        if (BOT.isFallFlying()) {
            boolean fire = heldIsFirework();
            if (!fire) ensureFireworkHeld();
            if (fire) ticksSinceFire = 0;
            submitInput(false, fire, yaw, -10f);
            phase = CONFIG.client.extra.elytraPilot.ebounce ? Phase.BOUNCE : Phase.CRUISE;
            info("Elytra swapped — resuming flight");
            return;
        }
        jumpToggle = !jumpToggle;
        submitInput(jumpToggle, false, yaw, 0f);
        if (++swapTicks > SWAP_REDEPLOY_TIMEOUT_TICKS && heightAboveGround(x, y, z) < cfg.minSwapClearance)
            phase = Phase.EMERGENCY;
    }

    private void tickDescend(double x, double y, double z, double speed) {
        var cfg = CONFIG.client.extra.elytraPilot;
        float yaw = desiredYaw(x, z);
        double horiz = horizDist(x, z, cfg.targetX, cfg.targetZ);
        int clearance = heightAboveGround(x, y, z);
        // Aim the nose down toward the target (steeper when high and close), gliding in without fireworks.
        double drop = Math.max(0, y - cfg.approxGroundY);
        float aim = (float) Math.toDegrees(Math.atan2(drop, Math.max(horiz, 1.0)));
        boolean overCap = speed * 20.0 >= cfg.maxSpeed;
        boolean blocked = terrainBlockedAhead(x, y, z, yaw, 8);
        float pitch = blocked ? -25f : clampF(aim, cfg.glidePitch, 30f); // cap the dive angle to limit speed
        if (overCap) pitch = Math.min(pitch, 0f);                         // level/raise the nose to bleed speed under the cap
        boolean fire = blocked && !overCap && heldIsFirework(); // only boost to avoid a crash; otherwise bleed altitude
        if (blocked && !fire) ensureFireworkHeld();
        if (fire) ticksSinceFire = 0;
        submitInput(false, fire, yaw, pitch);

        if (clearance <= 3 || (horiz <= cfg.arriveRadius && clearance <= 16)) {
            phase = Phase.LAND;
            landTicks = 0;
            info("Over target — landing");
        }
    }

    private void tickLand() {
        if (!BOT.isFallFlying()) { complete("landed"); return; }
        submitInput(false, false, CACHE.getPlayerCache().getYaw(), 25f); // glide down, no boost
        if (++landTicks > LAND_TIMEOUT_TICKS) complete("landing timed out");
    }

    private void tickEmergency(double x, double y, double z) {
        if (!emergencyAlerted) {
            inGameAlertActivePlayer("<red>ElytraPilot EMERGENCY — out of usable elytra; descending, please intervene");
            warn("EMERGENCY — no usable elytra/recovery");
            emergencyAlerted = true;
        }
        float yaw = CACHE.getPlayerCache().getYaw();
        if (BOT.isFallFlying()) {
            submitInput(false, false, yaw, 25f);                  // glide down as gently as possible
        } else if (wornElytraDurability() > 0) {
            jumpToggle = !jumpToggle;                              // any elytra left? deploy it to slow the fall
            submitInput(jumpToggle, false, yaw, 0f);
        } else {
            submitInput(false, false, yaw, 0f);                   // nothing left — falling
        }
        if (!BOT.isFallFlying() && heightAboveGround(x, y, z) <= 2) complete("emergency landing complete");
    }

    /** Called from CRUISE/DESCEND when the bot is unexpectedly not fall-flying. */
    private void handleLostFlight(double x, double y, double z) {
        var cfg = CONFIG.client.extra.elytraPilot;
        if (wornElytraDurability() > 2) {            // still wearing a working elytra — just re-deploy
            jumpToggle = !jumpToggle;
            boolean fire = BOT.isFallFlying() && heldIsFirework();
            submitInput(jumpToggle, fire, desiredYaw(x, z), 0f);
            if (fire) ticksSinceFire = 0;
            return;
        }
        if (cfg.swapElytra && hasSpareElytra()) { enterSwap(); return; }
        phase = Phase.EMERGENCY;
    }

    private void enterSwap() {
        phase = Phase.SWAP;
        swapRedeploying = false;
        swapTicks = 0;
        info("Elytra worn out — swapping in a fresh one");
    }

    // --- elytra swap mechanics ---

    /**
     * Issues at most one inventory action per call (and only when no inventory request is in flight) to move a
     * fresh elytra into the chestplate slot, re-reading the cache each time so every packet's slot prediction
     * is correct (Grim-safe). Returns true once the chestplate holds a fresh elytra and the cursor is empty.
     */
    private boolean equipFreshElytraProgress() {
        if (INVENTORY.hasActiveRequest()) return false; // let the previous action settle
        ItemStack mouse = CACHE.getPlayerCache().getInventoryCache().getMouseStack();
        boolean mouseEmpty = isEmpty(mouse);
        ItemStack chest = chestplate();

        if (mouseEmpty && isFreshElytra(chest)) return true;

        if (mouseEmpty) {
            int h = findFreshElytraHotbar();
            if (h >= 0) { // fast path: single MOVE_TO_HOTBAR_SLOT swap (chestplate <-> hotbar slot)
                submitInvAction(new MoveToHotbarSlot(CHESTPLATE_SLOT, MoveToHotbarAction.from(h - 36)));
                return false;
            }
            int m = findFreshElytraMain();
            if (m >= 0) { submitInvAction(new ClickItem(m, ClickItemAction.LEFT_CLICK)); return false; } // pick up fresh
            return false; // no spare (caller handles via timeout)
        }

        // cursor holds something
        if (isFreshElytra(mouse)) { // place the fresh elytra into the chestplate (cursor then holds the old one)
            submitInvAction(new ClickItem(CHESTPLATE_SLOT, ClickItemAction.LEFT_CLICK));
            return false;
        }
        int free = findEmptyPlayerSlot(); // stash whatever we're holding (the old elytra)
        if (free >= 0) { submitInvAction(new ClickItem(free, ClickItemAction.LEFT_CLICK)); return false; }
        warn("No free slot to stash the worn elytra during swap");
        return false;
    }

    private boolean needsElytraSwap() {
        return wornElytraDurability() <= CONFIG.client.extra.elytraPilot.elytraMinDurability;
    }

    private boolean hasSpareElytra() {
        return findFreshElytraHotbar() >= 0 || findFreshElytraMain() >= 0;
    }

    private int findFreshElytraHotbar() {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int s = 36; s <= 44; s++) if (isFreshElytra(inv.get(s))) return s;
        return -1;
    }

    private int findFreshElytraMain() {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int s = 9; s <= 35; s++) if (isFreshElytra(inv.get(s))) return s;
        return -1;
    }

    private int findEmptyPlayerSlot() {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int s = 9; s <= 44; s++) if (isEmpty(inv.get(s))) return s;
        return -1;
    }

    private ItemStack chestplate() {
        return CACHE.getPlayerCache().getPlayerInventory().get(CHESTPLATE_SLOT);
    }

    private int wornElytraDurability() {
        ItemStack c = chestplate();
        return isElytra(c) ? remainingDurability(c) : 0;
    }

    private boolean isElytra(ItemStack s) {
        if (isEmpty(s)) return false;
        return ItemRegistry.REGISTRY.get(s.getId()) == ItemRegistry.ELYTRA;
    }

    private boolean isFreshElytra(ItemStack s) {
        return isElytra(s) && remainingDurability(s) > CONFIG.client.extra.elytraPilot.freshElytraMinDurability;
    }

    /** Remaining durability of a stack (MAX_DAMAGE - DAMAGE). Non-damageable items count as unlimited. */
    private int remainingDurability(ItemStack s) {
        var data = ItemRegistry.REGISTRY.get(s.getId());
        if (data == null) return 0;
        Integer maxDamage = data.components().get(DataComponentTypes.MAX_DAMAGE);
        if (maxDamage == null) return Integer.MAX_VALUE;
        Integer damage = s.getDataComponentsOrEmpty().get(DataComponentTypes.DAMAGE);
        return maxDamage - (damage == null ? 0 : damage);
    }

    // --- control + sensing helpers ---

    private void submitInput(boolean jump, boolean fire, float yaw, float pitch) {
        submitMove(false, jump, false, fire, yaw, pitch);
    }

    private void submitMove(boolean forward, boolean jump, boolean sprint, boolean fire, float yaw, float pitch) {
        INPUTS.submit(InputRequest.builder()
            .owner(this)
            .input(Input.builder()
                .pressingForward(forward)
                .jumping(jump)
                .sprinting(sprint)
                .rightClick(fire)
                .hand(Hand.MAIN_HAND)
                .clickTarget(ClickTarget.None.INSTANCE)
                .clickRequiresRotation(false)
                .build())
            .yaw(yaw)
            .pitch(pitch)
            .priority(CONFIG.client.extra.elytraPilot.inputPriority)
            .build());
    }

    /** Re-engage the elytra (the bounce re-deploy). Same packet Bot.java sends on auto-deploy. */
    private void sendStartFallFlying() {
        sendClientPacketAsync(new ServerboundPlayerCommandPacket(
            CACHE.getPlayerCache().getEntityId(), PlayerState.START_ELYTRA_FLYING));
    }

    private void submitInvAction(InventoryAction... actions) {
        INVENTORY.submit(InventoryActionRequest.builder()
            .owner(this)
            .actions(actions)
            .priority(CONFIG.client.extra.elytraPilot.inputPriority)
            .build());
    }

    private float desiredYaw(double x, double z) {
        var cfg = CONFIG.client.extra.elytraPilot;
        if (cfg.highway) {
            // Pure-pursuit along a highway centerline through (0,0): project the bot onto the road, then aim at a
            // point further along it. This both heads down the road AND corrects sideways drift back to center.
            double[] d = highwayUnit(cfg.highwayDir);
            double t = x * d[0] + z * d[1];                  // how far along the road we are
            double aimX = (t + cfg.highwayLookahead) * d[0];
            double aimZ = (t + cfg.highwayLookahead) * d[1];
            return (float) Math.toDegrees(Math.atan2(-(aimX - x), aimZ - z));
        }
        if (!cfg.hasTarget) return cfg.heading;
        double dx = cfg.targetX - x;
        double dz = cfg.targetZ - z;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Unit direction vector (dx, dz) of a nether highway. MC: +X=east, +Z=south. Diagonals normalised. */
    private double[] highwayUnit(HighwayDir d) {
        final double s = 0.7071067811865476; // 1/sqrt(2)
        return switch (d) {
            case N  -> new double[]{ 0, -1 };
            case S  -> new double[]{ 0,  1 };
            case E  -> new double[]{ 1,  0 };
            case W  -> new double[]{-1,  0 };
            case NE -> new double[]{ s, -s };
            case SE -> new double[]{ s,  s };
            case SW -> new double[]{-s,  s };
            case NW -> new double[]{-s, -s };
        };
    }

    private boolean terrainBlockedAhead(double x, double y, double z, float yaw, int look) {
        double yawRad = Math.toRadians(yaw);
        double lx = -Math.sin(yawRad);
        double lz = Math.cos(yawRad);
        int fy = MathHelper.floorI(y);
        for (int i = 1; i <= look; i++) {
            int bx = MathHelper.floorI(x + lx * i);
            int bz = MathHelper.floorI(z + lz * i);
            if (!World.isChunkLoadedChunkPos(bx >> 4, bz >> 4)) return false; // unknown ahead — fly straight
            for (int dy = 0; dy <= 2; dy++) {
                var b = World.getBlock(bx, fy + dy, bz);
                if (!b.isAir() && !World.isFluid(b)) return true;
            }
        }
        return false;
    }

    /** Blocks of clear air directly below the bot before the first solid/ground (capped scan). */
    private int heightAboveGround(double x, double y, double z) {
        int bx = MathHelper.floorI(x);
        int bz = MathHelper.floorI(z);
        if (!World.isChunkLoadedChunkPos(bx >> 4, bz >> 4)) return Integer.MAX_VALUE;
        int fy = MathHelper.floorI(y);
        for (int dy = 1; dy <= 320; dy++) {
            int yy = fy - dy;
            if (yy < -64) break;
            var b = World.getBlock(bx, yy, bz);
            if (!b.isAir() && !World.isFluid(b)) return dy;
        }
        return Integer.MAX_VALUE;
    }

    private boolean heldIsFirework() {
        var pc = CACHE.getPlayerCache();
        return isFirework(pc.getPlayerInventory().get(36 + pc.getHeldItemSlot()));
    }

    private int findHotbarFirework() {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int s = 36; s <= 44; s++) if (isFirework(inv.get(s))) return s;
        return -1;
    }

    private void ensureFireworkHeld() {
        int slot = findHotbarFirework();
        if (slot < 0) return;
        int hotbarIdx = slot - 36;
        if (hotbarIdx == CACHE.getPlayerCache().getHeldItemSlot()) return;
        if (INVENTORY.hasActiveRequest()) return;
        submitInvAction(new SetHeldItem(hotbarIdx));
    }

    private boolean isFirework(ItemStack s) {
        if (isEmpty(s)) return false;
        return ItemRegistry.REGISTRY.get(s.getId()) == ItemRegistry.FIREWORK_ROCKET;
    }

    private static boolean isEmpty(ItemStack s) {
        return s == null || s == Container.EMPTY_STACK;
    }

    private static double horizDist(double x, double z, double tx, double tz) {
        return Math.hypot(tx - x, tz - z);
    }

    private static float clampF(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private void complete(String why) {
        phase = Phase.DONE;
        BARITONE.stop();
        inGameAlertActivePlayer("<green>ElytraPilot: " + why);
        info("Flight complete: " + why);
    }

    private void abort(String why) {
        phase = Phase.DONE;
        BARITONE.stop();
        inGameAlertActivePlayer("<red>ElytraPilot aborted: " + why);
        warn("Aborted: " + why);
    }
}
