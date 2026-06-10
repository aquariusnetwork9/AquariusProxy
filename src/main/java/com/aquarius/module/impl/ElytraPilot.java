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
import com.aquarius.mc.dimension.DimensionRegistry;
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

    private enum Phase { IDLE, TAKEOFF, CRUISE, BOUNCE, HOP, PASS, SWAP, DESCEND, LAND, LANDWALK, EMERGENCY, DONE }

    private static final int CHESTPLATE_SLOT = 6;          // container 0: 5=helm,6=chest,7=legs,8=boots
    private static final int TAKEOFF_TIMEOUT_TICKS = 200;  // ~10s to get airborne + deployed
    private static final int LAND_TIMEOUT_TICKS = 200;
    private static final int SWAP_EQUIP_TIMEOUT_TICKS = 100;
    private static final int SWAP_REDEPLOY_TIMEOUT_TICKS = 100;
    private static final int PROBE_INTERVAL = 10;           // recompute terrain scans every N ticks
    private static final int OPEN_TARGET_TOLERANCE = 8;     // land at the target if its open surface is within this of approxGroundY
    private static final int LANDWALK_TIMEOUT_TICKS = 1200; // ~60s for Baritone to reach the target on the ground
    private static final int HOP_MIN_TICKS = 6;             // commit to a hop for at least this long so we clear the block
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

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
    // terrain-aware approach / re-route / landing-spot search
    private int landX, landZ, landGroundY;
    private boolean landIsTarget;
    private boolean haveLandSpot;
    private float rerouteYaw;
    private boolean haveReroute;
    private boolean baritoneStarted;
    private int landWalkTicks;
    private int hopTicks;

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
        if (!hasAnyFirework())
            inGameAlertActivePlayer("<yellow>ElytraPilot: no firework rockets (hotbar or inventory) — flight will not sustain");
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

    // --- public hooks for the trip planner to drive individual flight legs ---

    /** True once the current flight leg has finished (landed/aborted). */
    public boolean isFlightDone() {
        return phase == Phase.DONE || phase == Phase.IDLE;
    }

    /** (Re)start a flight leg with whatever target/mode is currently configured — re-arms even if already enabled. */
    public void beginFlight() {
        CONFIG.client.extra.elytraPilot.enabled = true;
        syncEnabledFromConfig();   // register the tick handler (runs onEnable if it was disabled)
        reset();                   // re-arm cleanly even if a previous leg left us in DONE
        phase = CONFIG.client.extra.elytraPilot.ebounce ? Phase.BOUNCE : Phase.TAKEOFF;
    }

    /** Stop the current flight leg (disable the module). */
    public void endFlight() {
        CONFIG.client.extra.elytraPilot.enabled = false;
        syncEnabledFromConfig();
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
        haveLandSpot = false;
        landIsTarget = false;
        haveReroute = false;
        baritoneStarted = false;
        landWalkTicks = 0;
        hopTicks = 0;
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
                    case HOP       -> tickHop(x, y, z, speed);
                    case PASS      -> tickPass(x, y, z, speed);
                    case SWAP      -> tickSwap(x, y, z);
                    case DESCEND   -> tickDescend(x, y, z, speed);
                    case LAND      -> tickLand();
                    case LANDWALK  -> tickLandWalk(x, y, z);
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
        float yaw = steerYaw(x, y, z);

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

        // Open nether (off-highway): on 2b2t the bedrock roof is INACCESSIBLE and modern nether terrain fills the
        // upper layers, so the bot is almost never near the ceiling out here — don't climb to a ceiling. Fly level
        // at a clear altitude and react to obstacles in 3D (over / under / around). Highways stay on the bounce path.
        if (inNether() && !cfg.highway) { tickNetherCruise(x, y, z, speed); return; }

        // Overworld long-haul profile: firework-climb (nose up) to the ceiling, then glide (nose ≈ +2) to the floor.
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
            // Firework climb — but coast (no boost) the last stretch into the ceiling instead of powering past it
            // and wasting rockets; ease the nose toward glide as we approach. Conserves fireworks on long hauls.
            boolean nearCeiling = y >= cfg.glideCeilingY - cfg.climbStopMargin;
            pitch = nearCeiling ? cfg.glidePitch : -cfg.climbPitch; // nose-up ascent = max height per firework
            wantFire = !overCap && !nearCeiling
                && (speed < cfg.minBoostSpeed || ticksSinceFire >= cfg.maxBoostIntervalTicks);
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
     * Open-nether level flight. On 2b2t the bedrock roof is inaccessible and modern nether terrain fills the upper
     * layers, so the bot flies at a clear mid-altitude ({@code netherCruiseY}) rather than the ceiling and reacts to
     * obstacles in 3D: reroute horizontally (steerYaw), else climb over or dive under — whichever side has more room
     * (never into the bedrock roof or into lava). Sustains level flight with periodic fireworks (no free roof glide).
     */
    private void tickNetherCruise(double x, double y, double z, double speed) {
        var cfg = CONFIG.client.extra.elytraPilot;
        float yaw = steerYaw(x, y, z);                          // horizontal reroute around walls (±maxRerouteDeg)
        boolean overCap = speed * 20.0 >= cfg.maxSpeed;
        int roofCap = Math.min(cfg.netherCeilingY, 125);        // never climb into the (inaccessible) bedrock roof
        double cruiseAlt = Math.min(Math.max(cfg.netherCruiseY, cfg.approxGroundY + 8), roofCap - 2);

        float pitch;
        boolean wantFire;
        if (terrainBlockedAhead(x, y, z, yaw, Math.min(cfg.lookAheadBlocks, 24))) {
            // still walled after the horizontal reroute -> go vertical, toward whichever side has more clearance
            int above = clearAboveCount(x, y, z);
            int below = heightAboveGround(x, y, z);             // stops at lava (lava scans as solid) -> we won't dive into it
            boolean canClimb = y < roofCap - 1 && above > 3;
            boolean canDive = below > 4;
            if (canClimb && (!canDive || above >= below)) { pitch = -cfg.climbPitch * 0.7f; wantFire = !overCap; }
            else if (canDive)                              { pitch = 22f; wantFire = false; }              // dive under
            else                                           { pitch = -cfg.climbPitch * 0.7f; wantFire = !overCap; } // boxed in -> try up
        } else if (y < cruiseAlt - 2) {
            pitch = -12f; wantFire = !overCap;                  // below the band -> climb back up
        } else if (y > cruiseAlt + 2) {
            pitch = 8f; wantFire = false;                       // above it -> ease down (free)
        } else {
            pitch = -2f;                                        // holding altitude -> occasional boost to stay level
            wantFire = !overCap && (speed < cfg.minBoostSpeed || ticksSinceFire >= cfg.maxBoostIntervalTicks);
        }
        boolean fire = wantFire && heldIsFirework();
        if (wantFire && !fire) ensureFireworkHeld();
        if (fire) ticksSinceFire = 0;
        submitInput(false, fire, yaw, pitch);

        if (cfg.hasTarget) {
            double lead = Math.max(cfg.descendRadius, (y - cfg.approxGroundY) * cfg.glideRatio);
            if (horizDist(x, z, cfg.targetX, cfg.targetZ) <= lead) {
                phase = Phase.DESCEND;
                info("Approaching target — descending");
            }
        }
    }

    /** Blocks of clear (air/water, non-lava) space directly above the bot before the first solid/lava (capped). */
    private int clearAboveCount(double x, double y, double z) {
        int bx = MathHelper.floorI(x), bz = MathHelper.floorI(z), fy = MathHelper.floorI(y);
        if (!World.isChunkLoadedChunkPos(bx >> 4, bz >> 4)) return Integer.MAX_VALUE;
        for (int dy = 1; dy <= 64; dy++) {
            var b = World.getBlock(bx, fy + dy, bz);
            if (!b.isAir() && !World.isWater(b)) return dy;
        }
        return 64;
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

        // Obstacle handling: glide OVER a block/lava patch ahead (fast, stays in flight); only settle + walk (PASS)
        // when we actually stall hard against something. terrainBlockedAhead now treats lava as a hazard too.
        if (cfg.passObstacles) {
            if (speed * 20.0 < 2.0) bounceStallTicks++; else bounceStallTicks = 0;
            if (bounceStallTicks > cfg.bounceStallLimit) { enterPass(); return; }        // stuck against it -> walk past
            if (terrainBlockedAhead(x, y, z, yaw, Math.min(cfg.lookAheadBlocks, 12))) { enterHop(); return; } // glide over
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

    private void enterHop() {
        phase = Phase.HOP;
        hopTicks = 0;
        info("Obstacle on the road — gliding over it");
    }

    /** Glide up and over a block/lava patch on the road, then drop back into the bounce. Falls back to PASS if it can't clear. */
    private void tickHop(double x, double y, double z, double speed) {
        var cfg = CONFIG.client.extra.elytraPilot;
        if (y < cfg.roadY - cfg.roadDropAbort) { abort("fell off the road during a hop"); return; }
        float yaw = desiredYaw(x, z);                 // stay on the highway centerline; climb straight over
        boolean overCap = speed * 20.0 >= cfg.maxSpeed;
        if (!BOT.isFallFlying() && redeployCooldown <= 0) { sendStartFallFlying(); redeployCooldown = cfg.bounceRedeployTicks; }
        boolean fire = !overCap && heldIsFirework();
        if (!fire) ensureFireworkHeld();
        if (fire) ticksSinceFire = 0;
        submitMove(true, false, true, fire, yaw, -cfg.hopPitch); // forward+sprint, nose up, NO jump (don't bounce into it)
        boolean clearAhead = !terrainBlockedAhead(x, y, z, yaw, Math.min(cfg.lookAheadBlocks, 12));
        if (clearAhead && hopTicks >= HOP_MIN_TICKS) {
            phase = Phase.BOUNCE;
            bounceStallTicks = 0;
            info("Cleared — resuming bounce");
            return;
        }
        if (++hopTicks > cfg.hopTimeoutTicks) {
            if (cfg.passObstacles) enterPass(); else abort("blocked on the road and can't climb over it");
        }
    }

    private boolean inNether() {
        return World.getCurrentDimension() == DimensionRegistry.THE_NETHER.get();
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

    /**
     * Terrain-aware approach: steer toward a chosen landing spot (the target if it's open, otherwise the nearest
     * open flat ground near it), re-routing around terrain at flight level, and refusing to dive onto anything
     * between us and the spot (hold/climb over it instead — this is what stops it planting itself on a hill short
     * of the target). Only commits to landing once actually over the spot; Baritone finishes the last leg.
     */
    private void tickDescend(double x, double y, double z, double speed) {
        var cfg = CONFIG.client.extra.elytraPilot;
        if (!haveLandSpot || flightTicks % PROBE_INTERVAL == 0) computeLandSpot();
        float yaw = steerYaw(x, y, z);
        double horiz = horizDist(x, z, landX + 0.5, landZ + 0.5);
        int clearance = heightAboveGround(x, y, z);
        boolean overCap = speed * 20.0 >= cfg.maxSpeed;

        // Over the landing spot: drop straight in.
        if (horiz <= cfg.arriveRadius) {
            if (clearance <= 16) { phase = Phase.LAND; landTicks = 0; info("Over landing spot — landing"); return; }
            submitInput(false, false, yaw, overCap ? 0f : 28f);   // glide down (steep but speed-capped)
            return;
        }

        // Still approaching: don't dive onto terrain in the way — hold/climb over it; re-route handles walls.
        int bump = glideCorridorBlock(x, y, z);
        boolean wallAhead = clearDistAhead(x, y, z, yaw, Math.min(cfg.lookAheadBlocks, 32)) < 12;
        float pitch;
        boolean wantFire;
        if (bump >= 0 || wallAhead) {
            pitch = -Math.max(10f, cfg.climbPitch * 0.5f);        // gentle climb to clear it
            wantFire = !overCap;
        } else {
            double drop = Math.max(0, y - landGroundY);
            float aim = (float) Math.toDegrees(Math.atan2(drop, Math.max(horiz, 1.0)));
            pitch = clampF(aim, cfg.glidePitch, 30f);             // controlled glide down toward the spot
            if (overCap) pitch = Math.min(pitch, 0f);
            wantFire = false;
        }
        boolean fire = wantFire && heldIsFirework();
        if (wantFire && !fire) ensureFireworkHeld();
        if (fire) ticksSinceFire = 0;
        submitInput(false, fire, yaw, pitch);
    }

    private void tickLand() {
        var cfg = CONFIG.client.extra.elytraPilot;
        var pc = CACHE.getPlayerCache();
        double x = pc.getX(), y = pc.getY(), z = pc.getZ();
        if (!BOT.isFallFlying()) {
            double horiz = horizDist(x, z, cfg.targetX + 0.5, cfg.targetZ + 0.5);
            if (cfg.baritoneLand && cfg.hasTarget && horiz > cfg.arriveRadius) { enterLandWalk(); return; }
            complete("landed");
            return;
        }
        // Re-aim at the landing spot each tick and dive in, so we touch down ON it instead of gliding past it.
        float yaw = haveLandSpot
            ? (float) Math.toDegrees(Math.atan2(-(landX + 0.5 - x), landZ + 0.5 - z))
            : pc.getYaw();
        double horizSpot = haveLandSpot ? horizDist(x, z, landX + 0.5, landZ + 0.5) : 0;
        if (horizSpot > cfg.arriveRadius * 3 + 8) { phase = Phase.DESCEND; return; } // glided off — re-approach
        if (heightAboveGround(x, y, z) <= cfg.landCutClearance) BOT.stopFallFlying(); // low over the spot — cut glide, drop straight in
        submitInput(false, false, yaw, 45f);                                          // aggressive nose-down to touch down fast
        if (++landTicks > LAND_TIMEOUT_TICKS) { BOT.stopFallFlying(); complete("landing timed out"); }
    }

    // --- terrain-aware approach, re-route, landing-spot search, and Baritone walk-in ---

    private void enterLandWalk() {
        phase = Phase.LANDWALK;
        baritoneStarted = false;
        landWalkTicks = 0;
        BARITONE.stop();
        info("Landed near target — walking in with Baritone");
    }

    /** Ground-path the final leg to the exact target. Handles targets that are covered, indoors, or underground. */
    private void tickLandWalk(double x, double y, double z) {
        var cfg = CONFIG.client.extra.elytraPilot;
        double horiz = horizDist(x, z, cfg.targetX + 0.5, cfg.targetZ + 0.5);
        if (horiz <= cfg.arriveRadius) { BARITONE.stop(); complete("walked to target"); return; }
        if (!baritoneStarted) {
            int rsq = Math.max(1, cfg.arriveRadius * cfg.arriveRadius);
            BARITONE.pathTo(new GoalNear(cfg.targetX, cfg.approxGroundY, cfg.targetZ, rsq));
            baritoneStarted = true;
            landWalkTicks = 0;
            return;
        }
        if (!BARITONE.isActive() || ++landWalkTicks > LANDWALK_TIMEOUT_TICKS) {
            BARITONE.stop();
            complete(horiz <= cfg.arriveRadius + 6 ? "walked near target" : "landed near target; could not walk the last leg");
        }
    }

    /**
     * Steering yaw toward the target/heading, re-routed around terrain at flight level. When the direct line is
     * blocked, fan candidate headings out to ±maxRerouteDeg and take the smallest deviation that's clear (or the
     * clearest if none is fully open). Throttled to every PROBE_INTERVAL ticks for a smooth, cheap turn.
     */
    private float steerYaw(double x, double y, double z) {
        float base = desiredYaw(x, z);
        var cfg = CONFIG.client.extra.elytraPilot;
        if (!cfg.reroute || cfg.highway) return base; // highway has its own centerline tracking
        if (haveReroute && flightTicks % PROBE_INTERVAL != 0) return rerouteYaw;
        int look = cfg.lookAheadBlocks;
        int baseClear = clearDistAhead(x, y, z, base, look);
        if (baseClear >= look) { rerouteYaw = base; haveReroute = true; return base; }
        float bestYaw = base;
        int bestClear = baseClear;
        int maxDeg = (int) Math.max(0, cfg.maxRerouteDeg);
        for (int deg = 10; deg <= maxDeg; deg += 10) {
            for (int sign = -1; sign <= 1; sign += 2) {
                float cand = base + sign * deg;
                int c = clearDistAhead(x, y, z, cand, look);
                if (c >= look) { rerouteYaw = cand; haveReroute = true; return cand; } // clear, smallest deviation
                if (c > bestClear) { bestClear = c; bestYaw = cand; }
            }
        }
        rerouteYaw = bestYaw;
        haveReroute = true;
        return bestYaw;
    }

    /** Distance to the first solid terrain occupying the bot's flight level along {@code yaw}; {@code look} if clear/unknown. */
    private int clearDistAhead(double x, double y, double z, float yaw, int look) {
        double r = Math.toRadians(yaw);
        double lx = -Math.sin(r), lz = Math.cos(r);
        int fy = MathHelper.floorI(y);
        for (int d = 2; d <= look; d += 2) {
            int bx = MathHelper.floorI(x + lx * d);
            int bz = MathHelper.floorI(z + lz * d);
            if (!World.isChunkLoadedChunkPos(bx >> 4, bz >> 4)) return look; // can't see — treat as clear
            for (int dy = -1; dy <= 2; dy++) {
                var b = World.getBlock(bx, fy + dy, bz);
                if (!b.isAir() && !World.isWater(b)) return d;
            }
        }
        return look;
    }

    /**
     * Distance to the first terrain that pokes up through the straight glide line from the bot down to the landing
     * spot — the "scan ahead below the bot" needed so it doesn't plant itself on a hill short of the target. -1 = clear.
     */
    private int glideCorridorBlock(double x, double y, double z) {
        var cfg = CONFIG.client.extra.elytraPilot;
        double dx = landX + 0.5 - x, dz = landZ + 0.5 - z;
        double horiz = Math.hypot(dx, dz);
        if (horiz < 2) return -1;
        double ux = dx / horiz, uz = dz / horiz;
        double slope = (y - landGroundY) / horiz; // blocks of descent per block forward to reach the spot
        int limit = (int) Math.min(horiz - 2, cfg.lookAheadBlocks);
        for (int d = 4; d <= limit; d += 4) {
            int bx = MathHelper.floorI(x + ux * d);
            int bz = MathHelper.floorI(z + uz * d);
            int top = columnTop(bx, bz);
            if (top == Integer.MIN_VALUE) continue; // unloaded — skip
            double glideY = y - slope * d;
            if (top + cfg.pathClearance > glideY) return d;
        }
        return -1;
    }

    /** Pick where to set down: the target if it's open clear ground, else the nearest open, flat, safe spot near it. */
    private void computeLandSpot() {
        var cfg = CONFIG.client.extra.elytraPilot;
        haveLandSpot = true;
        int tx = cfg.targetX, tz = cfg.targetZ;
        int tTop = columnTop(tx, tz);
        if (tTop != Integer.MIN_VALUE && tTop > -64 && isOpenLanding(tx, tz)
                && Math.abs(tTop - cfg.approxGroundY) <= OPEN_TARGET_TOLERANCE) {
            landX = tx; landZ = tz; landGroundY = tTop; landIsTarget = true; return;
        }
        for (int rad = 1; rad <= cfg.landingSearchRadius; rad++) {
            int[] s = scanRing(tx, tz, rad);
            if (s != null) { landX = s[0]; landZ = s[1]; landGroundY = s[2]; landIsTarget = false; return; }
        }
        landX = tx; landZ = tz; landGroundY = cfg.approxGroundY; landIsTarget = false; // best effort; Baritone finishes
    }

    /** First open, flat landing column on the square ring at radius {@code rad} around (cx,cz); null if none. */
    private int[] scanRing(int cx, int cz, int rad) {
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != rad) continue; // ring perimeter only
                int bx = cx + dx, bz = cz + dz;
                if (isOpenLanding(bx, bz) && isFlat(bx, bz)) return new int[]{ bx, bz, columnTop(bx, bz) };
            }
        }
        return null;
    }

    private boolean isFlat(int bx, int bz) {
        int t = columnTop(bx, bz);
        if (t == Integer.MIN_VALUE || t <= -64) return false;
        for (int[] d : NEIGHBORS) {
            int n = columnTop(bx + d[0], bz + d[1]);
            if (n != Integer.MIN_VALUE && Math.abs(n - t) > 1) return false;
        }
        return true;
    }

    /** A column the bot can glide down onto: solid non-fluid top with 2 blocks of air above it. */
    private boolean isOpenLanding(int bx, int bz) {
        int top = columnTop(bx, bz);
        if (top == Integer.MIN_VALUE || top <= -64) return false;
        if (World.isFluid(World.getBlock(bx, top, bz))) return false; // no lava/water touchdown
        return World.getBlock(bx, top + 1, bz).isAir() && World.getBlock(bx, top + 2, bz).isAir();
    }

    /** Highest solid (non-air, non-fluid) block Y in a column. Integer.MIN_VALUE if the chunk isn't loaded. */
    private int columnTop(int bx, int bz) {
        if (!World.isChunkLoadedChunkPos(bx >> 4, bz >> 4)) return Integer.MIN_VALUE;
        int startY = Math.min(319, CONFIG.client.extra.elytraPilot.approxGroundY + 100);
        for (int yy = startY; yy >= -64; yy--) {
            var b = World.getBlock(bx, yy, bz);
            if (!b.isAir() && !World.isWater(b)) return yy;
        }
        return -65;
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
                if (!b.isAir() && !World.isWater(b)) return true;
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
            if (!b.isAir() && !World.isWater(b)) return dy;
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
        if (slot < 0) { refillFireworkToHotbar(); return; } // none in the hotbar — pull a stack down from the inventory
        int hotbarIdx = slot - 36;
        if (hotbarIdx == CACHE.getPlayerCache().getHeldItemSlot()) return;
        if (INVENTORY.hasActiveRequest()) return;
        submitInvAction(new SetHeldItem(hotbarIdx));
    }

    /** Move a firework stack from the main inventory into a hotbar slot. Supports multiple stacks (grabs the next one). */
    private void refillFireworkToHotbar() {
        if (INVENTORY.hasActiveRequest()) return;
        int m = findMainInvFirework();
        if (m < 0) return; // genuinely out of fireworks everywhere
        int button = findEmptyHotbarButton();
        if (button < 0) button = CACHE.getPlayerCache().getHeldItemSlot(); // no empty hotbar slot — swap into the held one
        submitInvAction(new MoveToHotbarSlot(m, MoveToHotbarAction.from(button)));
    }

    private int findMainInvFirework() {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int s = 9; s <= 35; s++) if (isFirework(inv.get(s))) return s;
        return -1;
    }

    private int findEmptyHotbarButton() {
        List<ItemStack> inv = CACHE.getPlayerCache().getPlayerInventory();
        for (int s = 36; s <= 44; s++) if (isEmpty(inv.get(s))) return s - 36;
        return -1;
    }

    /** Fireworks present anywhere the bot can use them (hotbar now, or inventory it can refill from). */
    private boolean hasAnyFirework() {
        return findHotbarFirework() >= 0 || findMainInvFirework() >= 0;
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
