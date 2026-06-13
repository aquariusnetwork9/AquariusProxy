package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.Proxy;
import com.aquarius.cache.data.inventory.Container;
import com.aquarius.event.client.ChunkDataEvent;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.event.client.ClientDeathEvent;
import com.aquarius.event.client.PlayerSetbackEvent;
import com.aquarius.event.module.TotemPopEvent;
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
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.Fireworks;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.BARITONE;
import static com.aquarius.Globals.BOT;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.INPUTS;
import static com.aquarius.Globals.INVENTORY;
import static com.aquarius.Globals.MODULE;

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

    private enum Phase { IDLE, TAKEOFF, CRUISE, BOUNCE, HOP, PASS, SWAP, DESCEND, LAND, LANDWALK, WALKOUT, EMERGENCY, DONE }

    private static final int CHESTPLATE_SLOT = 6;          // container 0: 5=helm,6=chest,7=legs,8=boots
    private static final int TAKEOFF_TIMEOUT_TICKS = 200;  // ~10s to get airborne + deployed
    private static final int LAND_TIMEOUT_TICKS = 300;  // brake zoom-climb + ~3 b/s flutter descent needs longer than the old dive
    private static final int SWAP_EQUIP_TIMEOUT_TICKS = 100;
    private static final int SWAP_REDEPLOY_TIMEOUT_TICKS = 100;
    private static final int PROBE_INTERVAL = 10;           // recompute terrain scans every N ticks
    private static final int OPEN_TARGET_TOLERANCE = 8;     // land at the target if its open surface is within this of approxGroundY
    private static final int LANDWALK_TIMEOUT_TICKS = 1200; // ~60s for Baritone to reach the target on the ground
    private static final int HOP_MIN_TICKS = 6;             // commit to a hop for at least this long so we clear the block
    private static final int EPISODE_DECAY_TICKS = 300;     // ~15s of healthy flight resets the lost-flight episode count
    private static final int PIN_WARN_TICKS = 100;           // ~5s of no cruise progress -> warn (likely pinned on terrain)
    private static final int PIN_ABORT_TICKS = 300;          // 2x this (~30s) frozen against terrain -> abort cleanly
    private static final int NATIVE_LOS_RANGE = 96;          // how far along the route to raytrace for the farthest VISIBLE aim point
    private static final int NATIVE_SCAN_WINDOW = 80;        // route points scanned forward per tick for the nearest-point tracker
    private static final int NATIVE_REPLAN_TICKS = 40;       // continuous replanning: a route older than this (~2s) is stale
    private static final int WALKOUT_LEG_BLOCKS = 48;        // Baritone walk distance per walk-out leg, toward the target
    private static final int WALKOUT_OPEN_SKY = 16;          // blocks of clear air overhead = enough room to fly again
    private static final int WALKOUT_TIMEOUT_TICKS = 1200;   // ~60s per walk-out leg
    private static final int MAX_WALKOUT_ATTEMPTS = 4;
    private static final float HIGHWAY_FRONTIER_FACTOR = 0.7f; // highways sit in previously-loaded, oft-reloaded chunks -> loading ~30% less of a problem; gentler governor than open nether
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
    private List<int[]> netherPath;   // current native full-route waypoints (block centers) in open nether
    private int pathIdx;
    private boolean netherPathIsNative;                              // netherPath came from the native router
    private java.util.concurrent.CompletableFuture<NetherRouter.Route> nativeFuture;
    private boolean nativeRouteFinished;
    private int nativeReqCooldown;
    private int routeAgeTicks;         // ticks since the active route was computed (staleness -> replan)
    private int routeLogSquelch;       // continuous replans: only log every Nth route
    private boolean nativeFailLogged;
    private boolean nativeUnsupportedLogged;
    private boolean lastFlightSuccess; // DONE via arrival/landing (true) vs abort/emergency (false)
    private int lostFlightTicks;       // continuous ticks of unexpected non-flight (watchdog)
    private int lostEpisodes;
    private int healthyTicks;          // continuous fall-flying ticks; sustained flight forgives past episodes
    private boolean lostLogged;
    private float landSpinYaw;         // helicopter-landing yaw spin
    private int pinTicks;              // consecutive cruise ticks at near-zero speed (wall-pin watchdog)
    private double stallAnchorX, stallAnchorZ; // window-start position for the net-progress stall watchdog
    private int stallWindowTicks;      // ticks since the last net-progress sample
    private int airborneTicks;         // ticks since this cruise began (climb-out window after takeoff)
    private int walkoutTicks;          // ticks in the current walk-out leg
    private int walkoutAttempts;
    private double loopX, loopZ;       // centre of a repeated takeoff-fail loop (chimney trap)
    private int loopCount;
    private boolean walkoutFar;        // suppress retakeoff until well away from the loop centre
    private int totemPops;             // totem pops during this flight (lethal-hit counter)
    private float lastHealth;          // -1 until first read; health drops drive the hazard responses
    private boolean hpDropped;         // health fell since the previous tick
    private int dmgWindowTicks;        // rolling window for counting in-flight damage events
    private int dmgCount;
    private int hazardClimbTicks;      // >0: climb + jink (repeated hits in flight — get out of the line of fire)
    private int hazardCooldown;        // re-arm delay: continuous damage (burning) must not hold the jink forever
    // --- simulation flight solver (the Baritone ElytraBehavior model) ---
    private final ExecutorService solverExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ElytraSolver");
        t.setDaemon(true);
        return t;
    });
    private Future<?> solverTask;                  // in-flight async solve (result lands in pendingSolution)
    private volatile FlightSolution pendingSolution;
    private volatile int setbackHoldTicks;         // no rockets while >0 (server just reset our position)
    private int boostGuaranteeTicks;               // ticks the live rocket is GUARANTEED to keep boosting
    private int boostMaxTicks;                     // ticks it might keep boosting (lifetime has random spread)

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.elytraPilot.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(ClientBotTick.Starting.class, e -> reset()),
            of(ClientBotTick.Stopped.class, e -> reset()),
            of(ClientDeathEvent.class, e -> {
                if (phase != Phase.IDLE && phase != Phase.DONE) abort("bot died");
            }),
            of(TotemPopEvent.class, this::onTotemPop),
            of(PlayerSetbackEvent.class, e -> {
                // Server reset our position (teleport/rubberband): never thrust against the correction —
                // hold all rockets briefly (Baritone's elytraFireworkSetbackUseDelay), and drop any queued
                // solution (it was computed from a position we are no longer at).
                if (phase != Phase.IDLE && phase != Phase.DONE) {
                    setbackHoldTicks = CONFIG.client.extra.elytraPilot.setbackHoldTicks;
                    pendingSolution = null;
                }
            }),
            of(ChunkDataEvent.class, e -> {     // feed observed chunks to the native router while flying the nether
                var cfg = CONFIG.client.extra.elytraPilot;
                if (cfg.nativeRouting && phase != Phase.IDLE && phase != Phase.DONE && inNether()
                        && NetherRouter.INSTANCE.isSupported()) {
                    NetherRouter.INSTANCE.submitChunk(e.x(), e.z(), cfg.netherSeed);
                }
            })
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

    /** True if the finished leg actually arrived/landed; false for aborts, deaths, and emergency landings. */
    public boolean wasFlightSuccessful() {
        return lastFlightSuccess;
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
        netherPath = null;
        pathIdx = 0;
        netherPathIsNative = false;
        nativeFuture = null;          // abandoned futures complete harmlessly on the router thread
        nativeRouteFinished = false;
        nativeReqCooldown = 0;
        routeAgeTicks = 0;
        routeLogSquelch = 0;
        nativeFailLogged = false;
        lastFlightSuccess = false;
        lostFlightTicks = 0;
        lostEpisodes = 0;
        healthyTicks = 0;
        lostLogged = false;
        pinTicks = 0;
        airborneTicks = 0;
        stallWindowTicks = 0;
        stallAnchorX = 0;
        stallAnchorZ = 0;
        walkoutTicks = 0;
        walkoutAttempts = 0;
        loopCount = 0;
        walkoutFar = false;
        totemPops = 0;
        lastHealth = -1f;
        hpDropped = false;
        dmgWindowTicks = 0;
        dmgCount = 0;
        hazardClimbTicks = 0;
        hazardCooldown = 0;
        if (solverTask != null) solverTask.cancel(true);
        solverTask = null;
        pendingSolution = null;
        setbackHoldTicks = 0;
        boostGuaranteeTicks = 0;
        boostMaxTicks = 0;
    }

    /**
     * Gate every rocket decision: the spacing cap (a rocket thrusts ~20 ticks, re-firing sooner is pure
     * waste) AND the post-setback hold (the server just reset our position — boosting against the
     * correction is an unwinnable fight; wait it out, then resume).
     */
    private boolean fireSpaced(boolean want) {
        return want
            && ticksSinceFire >= CONFIG.client.extra.elytraPilot.boostMinSpacingTicks
            && setbackHoldTicks <= 0;
    }

    /**
     * Record that a rocket was just fired so future decisions (and the solver's physics sims) know how long
     * the boost lasts: vanilla lifetime is {@code 10*(flight+1)} ticks guaranteed, plus up to 11 random more.
     */
    private void noteRocketFired() {
        ticksSinceFire = 0;
        int flight = 1;
        var pc = CACHE.getPlayerCache();
        ItemStack held = pc.getPlayerInventory().get(36 + pc.getHeldItemSlot());
        if (!isEmpty(held)) {
            Fireworks fw = held.getDataComponentsOrEmpty().get(DataComponentTypes.FIREWORKS);
            if (fw != null) flight = Math.max(1, fw.getFlightDuration());
        }
        boostGuaranteeTicks = 10 * (1 + flight);
        boostMaxTicks = boostGuaranteeTicks + 11;
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

            // Health bookkeeping: drops drive the grounded-damage flee and the in-flight hazard climb.
            float hp = pc.getThePlayer().getHealth();
            hpDropped = lastHealth >= 0f && hp < lastHealth - 0.01f;
            lastHealth = hp;
            if (hpDropped && BOT.isFallFlying() && (phase == Phase.CRUISE || phase == Phase.DESCEND)) {
                if (dmgWindowTicks <= 0) dmgCount = 0;
                dmgWindowTicks = 300;                     // 15s rolling window
                // One evasive burst, then a long cooldown: continuous damage (burning, lava splash) travels WITH
                // the bot — endlessly re-arming the jink just flew it in arcs through terrain. After the burst,
                // straight-and-fast is the best response to damage evasion can't shake.
                if (++dmgCount >= 3 && hazardClimbTicks <= 0 && hazardCooldown <= 0) {
                    hazardClimbTicks = 80;
                    hazardCooldown = 500;                 // ~25s before another evasive burst
                    warn("Repeated damage in flight ({} hits) at {}, {}, {} — hazard climb + jink",
                        dmgCount, (int) x, (int) y, (int) z);
                }
            }
            if (dmgWindowTicks > 0) dmgWindowTicks--;
            if (hazardClimbTicks > 0) hazardClimbTicks--;
            if (hazardCooldown > 0) hazardCooldown--;

            if (BOT.isFallFlying()) {
                lostFlightTicks = 0;
                lostLogged = false;
                // Sustained healthy flight forgives past flight-loss episodes AND walk-out attempts: the caps
                // should catch being stuck NOW, not tax every rough patch crossed over a long route (a successful
                // walkout early in a leg used to leave no budget for trouble an hour of terrain later).
                if ((lostEpisodes > 0 || walkoutAttempts > 0) && ++healthyTicks >= EPISODE_DECAY_TICKS) {
                    lostEpisodes = 0;
                    walkoutAttempts = 0;
                    healthyTicks = 0;
                }
            } else {
                healthyTicks = 0;
            }

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
                    case LAND      -> tickLand(x, y, z, speed);
                    case LANDWALK  -> tickLandWalk(x, y, z);
                    case WALKOUT   -> tickWalkout(x, y, z);
                    case EMERGENCY -> tickEmergency(x, y, z);
                    default        -> { }
                }
            }

            lastX = x;
            lastZ = z;
            haveLast = true;
            ticksSinceFire++;
            if (redeployCooldown > 0) redeployCooldown--;
            if (setbackHoldTicks > 0) setbackHoldTicks--;
            if (boostGuaranteeTicks > 0) boostGuaranteeTicks--;
            if (boostMaxTicks > 0) boostMaxTicks--;
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
            anchorStallWatch();                     // start net-progress tracking from the takeoff point
            info("Airborne — entering " + (CONFIG.client.extra.elytraPilot.ebounce ? "bounce" : "cruise"));
            return;
        }
        var cfg = CONFIG.client.extra.elytraPilot;
        if (hpDropped && cfg.hasTarget && walkoutAttempts < MAX_WALKOUT_ATTEMPTS) {
            takeoffTicks = 0;
            enterWalkout("taking damage during takeoff");
            return;
        }
        // No headroom here? Walk somewhere flyable FIRST instead of burning ~10s (and sometimes a totem)
        // discovering it the hard way against a ceiling.
        if (takeoffTicks == 0 && cfg.hasTarget && walkoutAttempts < MAX_WALKOUT_ATTEMPTS) {
            var pc = CACHE.getPlayerCache();
            if (clearAboveCount(pc.getX(), pc.getY(), pc.getZ()) < 6) {
                enterWalkout("no headroom to take off here");
                return;
            }
        }
        ensureFireworkHeld();
        boolean jump;
        if (cfg.doubleJumpTakeoff) {
            jump = jumpToggle;          // alternate press/release so a fresh jump edge lands while airborne
            jumpToggle = !jumpToggle;
        } else {
            jump = true;                // assume already airborne (ledge/tower) — just deploy
        }
        submitInput(jump, false, CACHE.getPlayerCache().getYaw(), 0f);
        if (++takeoffTicks > TAKEOFF_TIMEOUT_TICKS) {
            // No headroom here (pocket/overhang)? Walk toward open ground and retry before giving up —
            // but only if an elytra is actually worn (walking can't fix a missing elytra).
            if (cfg.hasTarget && isElytra(chestplate()) && walkoutAttempts < MAX_WALKOUT_ATTEMPTS) {
                takeoffTicks = 0;
                enterWalkout("could not take off here");
            } else {
                abort("could not take off (need open sky above + a worn elytra)");
            }
        }
    }

    /**
     * (Re)start the net-progress stall watchdog from the current position — call on each entry into cruise.
     * Starts with a grace period: a fresh takeoff spends its first seconds deploying + climbing with little
     * horizontal travel, and checking the first window immediately put the bot in a walkout->takeoff->walkout
     * loop (live: "stuck (10b net in 3s)" fired at y112 — it was CLIMBING, not stalled).
     */
    private void anchorStallWatch() {
        var pc = CACHE.getPlayerCache();
        stallAnchorX = pc.getX();
        stallAnchorZ = pc.getZ();
        stallWindowTicks = -2 * STALL_WINDOW_TICKS;   // ~6s grace before the first 3s window is judged
        airborneTicks = 0;                            // restart the post-takeoff climb-out window
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
                if (!fire) ensureFireworkHeld(); else noteRocketFired();
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

        // Open nether (off-highway): follow the native route through the terrain. The route threads the holes,
        // so the bot does NOT cruise at an altitude, climb over obstacles, or fly up looking for open air —
        // there is none. The only watchdog is a genuine wall-pin: frozen against terrain while still fall-flying.
        if (inNether() && !cfg.highway) {
            if (speed * 20.0 < 2.0) {
                pinTicks++;
                // Pin = the route assumed a way through that the real terrain blocks. Replan immediately (fresh
                // chunks bend the route through a different opening); do NOT walk out or climb to escape.
                if (pinTicks == 1) { nativeReqCooldown = 0; routeAgeTicks = NATIVE_REPLAN_TICKS; }
                if (pinTicks == PIN_WARN_TICKS)
                    warn("No cruise progress at {}, {}, {} — replanning the route through a different gap", (int) x, (int) y, (int) z);
                if (pinTicks > PIN_ABORT_TICKS * 2) {   // ~30s genuinely stuck — stop cleanly, never thrash
                    abort("pinned against terrain at " + (int) x + ", " + (int) y + ", " + (int) z);
                    return;
                }
            } else {
                pinTicks = 0;
            }
            tickNetherCruise(x, y, z, speed);
            return;
        }

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
        wantFire = fireSpaced(wantFire);
        boolean fire = wantFire && heldIsFirework();
        if (wantFire && !fire) ensureFireworkHeld();
        if (fire) noteRocketFired();
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
     * Open-nether flight. The babbaj/nether-pathfinder route IS the flight plan — full-leg waypoints threaded
     * THROUGH the terrain (including unloaded chunks generated from the seed in C++); the bot simply follows
     * them. There is no cruise altitude and no climbing over obstacles — the route already goes through the
     * openings. While a route is still computing (~0.3-3s), or on the rare system without native support, it
     * falls back to {@link #tickNetherBridge} — a brief level hold heading at the target, never a climb.
     */
    private void tickNetherCruise(double x, double y, double z, double speed) {
        var cfg = CONFIG.client.extra.elytraPilot;
        if (cfg.nativeRouting && cfg.hasTarget && NetherRouter.INSTANCE.isSupported()) {
            tickNetherNative(x, y, z, speed);
            return;
        }
        if (cfg.nativeRouting && !NetherRouter.INSTANCE.isSupported() && !nativeUnsupportedLogged) {
            nativeUnsupportedLogged = true;
            warn("Native nether routing unsupported on this system — holding heading toward the target");
        }
        tickNetherBridge(x, y, z, speed);                       // `native off`, no target, or unsupported
    }

    /**
     * Fly the native route THROUGH the terrain. The route leads and the bot follows it — via the physics solver
     * when it has a collision-free solution, else by geometric pure-pursuit of the route. It follows the route's
     * own altitude (up and down through the openings) and NEVER climbs toward open air or flies over obstacles;
     * when the way is blocked it replans through a different gap. Only the bedrock-roof cap and a sideways
     * ghast-fire jink override the route.
     */
    private void tickNetherNative(double x, double y, double z, double speed) {
        var cfg = CONFIG.client.extra.elytraPilot;
        boolean overCap = speed * 20.0 >= cfg.maxSpeed;
        int roofCap = Math.min(cfg.netherCeilingY, 125);

        maintainNativeRoute(x, y, z, cfg);
        if (!netherPathIsNative || netherPath == null || netherPath.size() < 2) {
            tickNetherBridge(x, y, z, speed);                   // brief level hold while the route computes (~0.3-3s)
            return;
        }

        // PURE PURSUIT (the Baritone way). Track progress by the horizontally-nearest route point in a forward
        // window — NO vertical gate. The old per-waypoint advance required |y - wp.y| < 6, but the route's y is
        // advisory (it hugs y~45 over lava seas while the descent guard holds the bot higher), so the index froze
        // and the bot orbited a waypoint it could never "touch" (live capture: endless 2-block circle at y60).
        int scanEnd = Math.min(pathIdx + NATIVE_SCAN_WINDOW, netherPath.size());
        double nearestD = Double.MAX_VALUE;
        for (int i = pathIdx; i < scanEnd; i++) {
            int[] p = netherPath.get(i);
            double d = horizDist(x, z, p[0], p[2]);
            if (d < nearestD) { nearestD = d; pathIdx = i; }
        }

        // SIMULATION SOLVER (the Baritone ElytraBehavior model): if the async solver produced a solution for
        // this tick — an aim point + pitch whose simulated future was verified collision-free against the
        // native terrain cache — fly it and skip the heuristics entirely. The heuristics below remain the
        // bridge while the solver is busy, boxed in (no collision-free future), or turned off.
        FlightSolution sol = cfg.solver ? takeSolution(x, y, z) : null;
        if (sol != null) {
            flySolution(x, y, z, sol, overCap, roofCap);
            maybeBeginDescent(x, y, z, cfg);
            return;
        }

        // NO SOLVER SOLUTION THIS TICK (solver still computing, or genuinely no collision-free trajectory):
        // pure-pursue the route by GEOMETRY. This is THE difference from every prior iteration — the fallback
        // aims AT the route, which the C++ pathfinder already threaded THROUGH the openings, and follows the
        // route's OWN altitude. It never climbs toward "open air" (there is none in the nether) and never tries
        // to fly over terrain (the route goes through it). When the way ahead is blocked, we replan and coast —
        // the fresh chunks bend the route through a different gap — we do not go up.
        int aimIdx = -1;
        final int lastIdx = netherPath.size() - 1;
        for (int i = pathIdx; i <= lastIdx; i++) {
            int[] p = netherPath.get(i);
            if (horizDist(x, z, p[0], p[2]) > NATIVE_LOS_RANGE) break;
            if (flightLineClear(x, y + 0.5, z, p[0] + 0.5, p[1] + 0.5, p[2] + 0.5)) aimIdx = i;  // farthest visible
        }
        if (aimIdx < 0) {                                       // can't see any route point — aim just ahead, replan now
            aimIdx = Math.min(pathIdx + 2, lastIdx);
            routeAgeTicks = NATIVE_REPLAN_TICKS;
            nativeReqCooldown = 0;
        }
        int[] w = netherPath.get(aimIdx);
        float yaw = (float) Math.toDegrees(Math.atan2(-(w[0] + 0.5 - x), w[2] + 0.5 - z));
        double dy = (w[1] + 0.5) - y;
        double horiz = Math.max(1.0, horizDist(x, z, w[0], w[2]));
        // Aim straight at the route point — follow its altitude up OR down through the openings. Clamp only for
        // physical sanity; this is pursuit, not a climb bias.
        float pitch = clampF((float) Math.toDegrees(Math.atan2(-dy, horiz)), -30f, 30f);
        boolean wantFire = !overCap && (speed < cfg.minBoostSpeed || ticksSinceFire >= cfg.maxBoostIntervalTicks);

        // Observed terrain blocks the heading the route assumed (regenerated chunks / builds): replan immediately
        // so the route bends through a different opening, and coast while it recomputes. We do NOT climb over it.
        if (terrainBlockedAhead(x, y, z, yaw, 8)) {
            nativeReqCooldown = 0;
            routeAgeTicks = NATIVE_REPLAN_TICKS;
            nativeRouteFinished = false;
            wantFire = false;
        }
        // Corner discipline: coast into sharp route bends instead of boosting through them.
        if (wantFire && aimIdx < lastIdx) {
            int[] w2 = netherPath.get(Math.min(aimIdx + 8, lastIdx));
            double b2 = Math.toDegrees(Math.atan2(-(w2[0] - w[0]), w2[2] - w[2]));
            if (Math.abs(wrapDeg(b2 - yaw)) > 35) wantFire = false;
        }
        // Hazard: jink sideways out of a ghast's line of fire — a YAW change only, never a climb.
        if (hazardClimbTicks > 0) yaw += 30f;
        // Safety only: never deliberately fly up into the inaccessible bedrock roof.
        if (y >= roofCap - 1 && pitch < 0f) pitch = 0f;

        wantFire = fireSpaced(wantFire);
        boolean fire = wantFire && heldIsFirework();
        if (wantFire && !fire) ensureFireworkHeld();
        if (fire) noteRocketFired();
        submitFlightAndSolve(x, y, z, fire, yaw, pitch, roofCap);

        maybeBeginDescent(x, y, z, cfg);
    }

    /** Begin the final descent once close enough to glide down to the target (lead scales with altitude). */
    private void maybeBeginDescent(double x, double y, double z, com.aquarius.util.config.Config.Client.Extra.ElytraPilot cfg) {
        double lead = Math.max(cfg.descendRadius, (y - cfg.approxGroundY) * cfg.glideRatio);
        if (horizDist(x, z, cfg.targetX, cfg.targetZ) <= lead) {
            phase = Phase.DESCEND;
            info("Approaching target — descending");
        }
    }

    /**
     * Reactive open-nether flight — the bridge used while the native route computes (~0.3-3s) and the fallback on
     * the rare system without native support. Always makes forward progress toward the target (never circles in
     * place): on 2b2t the bedrock roof is inaccessible and modern nether terrain fills the upper
     * layers, so the bot flies at a clear mid-altitude ({@code netherCruiseY}) rather than the ceiling and reacts to
     * obstacles in 3D: reroute horizontally (steerYaw), else climb over or dive under — whichever side has more room
     * (never into the bedrock roof or into lava). Sustains level flight with periodic fireworks (no free roof glide).
     */
    private void tickNetherBridge(double x, double y, double z, double speed) {
        var cfg = CONFIG.client.extra.elytraPilot;
        int roofCap = Math.min(cfg.netherCeilingY, 122);
        boolean overCap = speed * 20.0 >= cfg.maxSpeed;
        float yaw = desiredYaw(x, z);                           // straight at the target; the route takes over shortly
        // Near-level: a touch of lift ONLY if scraping the floor, otherwise hold level. No climb, no obstacle
        // dodging — threading terrain is the native route's job, and it is milliseconds away. This is just a
        // momentary hold so the bot doesn't sink while the first route computes.
        int below = heightAboveGround(x, y, z);
        float pitch = below < 8 ? -8f : -2f;
        if (y >= roofCap - 1 && pitch < 0f) pitch = 0f;
        boolean wantFire = fireSpaced(!overCap
            && (speed < cfg.minBoostSpeed || ticksSinceFire >= cfg.maxBoostIntervalTicks));
        boolean fire = wantFire && heldIsFirework();
        if (wantFire && !fire) ensureFireworkHeld();
        if (fire) noteRocketFired();
        submitInput(false, fire, yaw, pitch);
        maybeBeginDescent(x, y, z, cfg);
    }

    /**
     * Keep the native route CONTINUOUSLY recomputed from wherever the bot actually is — not just on big
     * deviation. Long straight pursuit lines don't survive the nether; the plan must stay fresh against the
     * ever-growing observed-chunk picture (this is what makes Baritone's flight feel alive). While a request
     * computes, the bot keeps flying the previous route — it never waits in place.
     */
    private void maintainNativeRoute(double x, double y, double z, com.aquarius.util.config.Config.Client.Extra.ElytraPilot cfg) {
        if (nativeReqCooldown > 0) nativeReqCooldown--;
        routeAgeTicks++;

        if (nativeFuture != null && nativeFuture.isDone()) {
            try {
                final NetherRouter.Route r = nativeFuture.join();
                if (r != null && r.points().size() >= 2) {
                    netherPath = r.points();
                    pathIdx = nearestRouteIndex(r.points(), x, z);
                    netherPathIsNative = true;
                    nativeRouteFinished = r.finished();
                    nativeFailLogged = false;
                    routeAgeTicks = 0;
                    if (--routeLogSquelch <= 0) {                // continuous replans: log ~every 10s, not every 2s
                        routeLogSquelch = 5;
                        info("Native route: {} waypoints{} ({} observed chunks fed{})", r.points().size(),
                            r.finished() ? "" : " (partial — will extend)", NetherRouter.INSTANCE.fedChunkCount(),
                            NetherRouter.INSTANCE.feedErrorCount() > 0
                                ? ", " + NetherRouter.INSTANCE.feedErrorCount() + " feed errors" : "");
                    }
                } else {
                    nativeReqCooldown = 100;                     // failing searches: back off to ~5s
                    if (!nativeFailLogged) {
                        nativeFailLogged = true;
                        warn("Native routing returned no route — flying reactive");
                    }
                }
            } catch (final Exception e) {
                nativeReqCooldown = 100;
                if (!nativeFailLogged) {
                    nativeFailLogged = true;
                    warn("Native routing failed ({}) — flying reactive", e.toString());
                }
            }
            nativeFuture = null;
        }

        boolean need;
        if (netherPathIsNative && netherPath != null) {
            final int[] cur = netherPath.get(Math.min(pathIdx, netherPath.size() - 1));
            final boolean deviated = horizDist(x, z, cur[0], cur[2]) > 48;
            final boolean nearEnd = !nativeRouteFinished && pathIdx >= netherPath.size() - 8;
            final boolean stale = routeAgeTicks >= NATIVE_REPLAN_TICKS;
            // Keep flying the current route while the replacement computes — never drop to a hold mid-flight.
            need = deviated || nearEnd || stale;
        } else {
            need = true;
        }
        if (need && nativeFuture == null && nativeReqCooldown <= 0) {
            nativeReqCooldown = NATIVE_REPLAN_TICKS;             // continuous replanning cadence (~2s)
            // Feed the loaded neighbourhood first so observation overrides generation where we actually are.
            final int pcx = MathHelper.floorI(x) >> 4, pcz = MathHelper.floorI(z) >> 4;
            for (int dx = -10; dx <= 10; dx++) {
                for (int dz = -10; dz <= 10; dz++) {
                    if (World.isChunkLoadedChunkPos(pcx + dx, pcz + dz)) {
                        NetherRouter.INSTANCE.submitChunk(pcx + dx, pcz + dz, cfg.netherSeed);
                    }
                }
            }
            final int destY = Math.min(Math.max(MathHelper.floorI(y), 48), 100);
            nativeFuture = NetherRouter.INSTANCE.requestRoute(
                MathHelper.floorI(x), MathHelper.floorI(y), MathHelper.floorI(z),
                cfg.targetX, destY, cfg.targetZ, cfg.netherSeed, cfg.nativeRouteTimeoutMs);
        }
    }

    /** Index of the route point nearest to (x,z) — where to resume following after a (re)plan. */
    private int nearestRouteIndex(List<int[]> route, double x, double z) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        final int limit = Math.min(route.size(), 400);
        for (int i = 0; i < limit; i++) {
            final int[] p = route.get(i);
            final double d = horizDist(x, z, p[0], p[2]);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    // ============================== simulation flight solver ==============================
    // The Baritone ElytraBehavior model, ported for the proxy. Each tick the flight input is chosen by
    // SIMULATION, not geometry: walk route points from far to near, build candidate aim positions (boosted
    // candidates also try extra altitude so a burning rocket buys height, with progressively relaxed safety
    // margins when boxed in), screen each candidate with an 8-corner hitbox raytrace through the native
    // terrain cache, then for every pitch around the direct line fly the REAL vanilla elytra physics
    // (incl. rocket thrust) forward tick by tick — any future that clips terrain is rejected outright, and
    // the survivor whose net displacement points best along the goal direction wins. The solve runs on its
    // own thread against a predicted next-tick state and is consumed the following tick, which is how
    // Baritone affords thousands of simulations per tick without stalling the game loop.

    /** An aim point + pitch whose simulated flight was verified collision-free. */
    private record FlightSolution(double startX, double startY, double startZ,
                                  double destX, double destY, double destZ,
                                  float pitch, boolean forceBoost) { }

    /** Immutable state snapshot the solver works from (built on the tick thread, read on the solver thread). */
    private record SolveSnapshot(double x, double y, double z,
                                 double vx, double vy, double vz,
                                 List<int[]> route, int pathIdx, int boostTicks,
                                 int simTicks, int pitchRange, int roofCap) { }

    /** Per-solve budget of pitch sweeps, so a fully boxed-in solve cannot run away (each sweep = ~50 sims). */
    private static final int SOLVE_PITCH_BUDGET = 40;

    /** Hand over the queued async solution unless the bot has jumped far from where it was solved. */
    private FlightSolution takeSolution(double x, double y, double z) {
        FlightSolution s = pendingSolution;
        pendingSolution = null;                    // single use
        if (s == null) return null;
        // The solve ran from the bot's real state LAST tick, so a normal tick of travel (~2 blocks) is
        // expected and fine — flySolution recomputes yaw live and only borrows the simulated pitch. Discard
        // only on a big jump (a teleport/rubberband the setback handler did not already clear the solution for).
        if (horizDist(x, z, s.startX(), s.startZ()) > 12 || Math.abs(y - s.startY()) > 12) return null;
        return s;
    }

    /**
     * Fly the solver's answer. Yaw is recomputed toward its aim point from where the bot is NOW (cheap and
     * exact); the simulated pitch is the hard-won part. Firework policy is Baritone's: a rocket only when not
     * already boosted, meaningfully short of the aim point, and actually slow — no fixed re-fire timer; plus
     * any desperate boost the simulation itself proved necessary. The few overrides the simulation cannot see
     * stay on top: mob fire (jink), the 2b2t speed cap, and the bedrock roof.
     */
    private void flySolution(double x, double y, double z, FlightSolution sol, boolean overCap, int roofCap) {
        var cfg = CONFIG.client.extra.elytraPilot;
        float yaw = (float) Math.toDegrees(Math.atan2(-(sol.destX() - x), sol.destZ() - z));
        float pitch = sol.pitch();
        if (hazardClimbTicks > 0) yaw += 30f;                   // line-of-fire jink — the sim can't see ghasts
        if (y >= roofCap - 1 && pitch < 0f) pitch = 0f;         // never climb into the inaccessible roof

        var vel = BOT.getVelocity();
        double vy = y < sol.destY() ? Math.max(0, vel.getY()) : vel.getY();  // climbing toward the aim: sink is fine
        double speed3d = Math.sqrt(vel.getX() * vel.getX() + vy * vy + vel.getZ() * vel.getZ());
        boolean shortOfAim = y < sol.destY() - 5 || horizDist(x, z, sol.destX(), sol.destZ()) > 5;
        boolean wantFire = sol.forceBoost()
            || (boostMaxTicks <= 0 && shortOfAim && speed3d * 20.0 < cfg.boostBelowSpeed);
        if (overCap && !sol.forceBoost()) wantFire = false;     // never thrust past the 2b2t speed cap
        wantFire = fireSpaced(wantFire);                        // spacing + post-setback rocket hold
        boolean fire = wantFire && heldIsFirework();
        if (wantFire && !fire) ensureFireworkHeld();
        if (fire) noteRocketFired();
        submitFlightAndSolve(x, y, z, fire, yaw, pitch, roofCap);
    }

    /**
     * Submit this tick's flight input, then queue the NEXT tick's solve from the predicted post-physics
     * state: one vanilla physics step with exactly the input just chosen. (Baritone solves from the real
     * post-tick state in an end-of-tick hook; the proxy's tick order means we predict it instead, and
     * {@link #takeSolution} discards the result if reality diverged.)
     */
    private void submitFlightAndSolve(double x, double y, double z, boolean fire, float yaw, float pitch, int roofCap) {
        submitInput(false, fire, yaw, pitch);
        var cfg = CONFIG.client.extra.elytraPilot;
        if (!cfg.solver || !netherPathIsNative || netherPath == null) return;
        if (solverTask != null && !solverTask.isDone()) return; // still busy — fallback pursuit carries this tick
        // Solve from the bot's CURRENT real state. The result is consumed next tick, ~1 tick (a couple blocks)
        // stale — acceptable, since flySolution recomputes the yaw live and only borrows the simulated pitch.
        // (Baritone predicts the post-tick state in an end-of-tick hook; the proxy has no such hook, and a real
        // physics prediction proved too fragile in chaotic terrain — every solution got discarded.)
        var vel = BOT.getVelocity();
        final SolveSnapshot snap = new SolveSnapshot(
            x, y, z, vel.getX(), vel.getY(), vel.getZ(),
            netherPath, pathIdx, boostGuaranteeTicks,
            Math.max(5, cfg.solverSimTicks), Math.max(5, cfg.solverPitchRange), roofCap);
        solverTask = solverExec.submit(() -> {
            try {
                pendingSolution = solveAngles(snap);
            } catch (final Throwable t) {
                pendingSolution = null;             // the solver must never take the flight down
            }
        });
    }

    /** The solver core: pick the farthest reachable aim point that has a collision-free simulated future. */
    private FlightSolution solveAngles(SolveSnapshot s) {
        final List<int[]> route = s.route();
        final int last = route.size() - 1;
        final int near = Math.min(s.pathIdx(), last);
        final int[] budget = { SOLVE_PITCH_BUDGET };

        for (int relaxation = 0; relaxation < 3; relaxation++) {
            // Fly the route's OWN altitude. The C++ pathfinder already threaded the route through the openings,
            // so the aim point's Y is the correct Y — we do NOT add upward candidates. There is no open layer
            // to climb to in the nether; biasing up is exactly the bug that failed every prior iteration.
            final int[] heights = {0};
            final double margin = relaxation == 0 ? 0.4 : relaxation == 1 ? 0.2 : 0.0;
            final int step = relaxation == 0 ? 2 : 1;           // strict pass skips every other point (cheap)
            for (int i = Math.min(near + 20, last); i >= near; i -= step) {
                final int[] p = route.get(i);
                for (final int dy : heights) {
                    final double cx = p[0] + 0.5, cy = p[1] + 0.5 + dy, cz = p[2] + 0.5;
                    if (cy > s.roofCap() - 1) continue;
                    if (dy != 0) {
                        // a climbing candidate must keep the onward route visible — never climb into a lip
                        final int[] onward = route.get(Math.min(i + 3, last));
                        if (!rayClear(cx, cy, cz, onward[0] + 0.5, onward[1] + 0.5, onward[2] + 0.5)) continue;
                    }
                    if (!hitboxClear(s, cx, cy, cz, margin)) continue;
                    final Float pitch = solvePitch(s, cx, cy, cz, relaxation, budget);
                    if (pitch != null) return new FlightSolution(s.x(), s.y(), s.z(), cx, cy, cz, pitch, false);
                    if (budget[0] <= 0) break;
                }
                if (budget[0] <= 0) break;
            }
            if (budget[0] <= 0) break;
        }

        // Desperate: nothing escapes unpowered. Would a rocket save us? Only force one the sim says works
        // (Baritone's forced-firework test) — blind panic boosts into terrain are how totems get eaten.
        final int[] p = route.get(Math.min(near + 4, last));
        for (final int delay : new int[]{3, 2, 1}) {
            final Float pitch = sweepPitches(s, p[0] + 0.5, p[1] + 0.5, p[2] + 0.5, true, s.simTicks(), 10, delay);
            if (pitch != null) return new FlightSolution(s.x(), s.y(), s.z(), p[0] + 0.5, p[1] + 0.5, p[2] + 0.5, pitch, true);
        }
        return null;
    }

    private Float solvePitch(SolveSnapshot s, double dx, double dy, double dz, int relaxation, int[] budget) {
        if (budget[0]-- <= 0) return null;
        final boolean desperate = relaxation == 2;
        final int ticks = desperate ? 3
            : s.boostTicks() > 0 ? Math.max(5, s.boostTicks())
            : s.simTicks();
        return sweepPitches(s, dx, dy, dz, desperate, ticks, s.boostTicks() > 0 ? ticks : 0, 0);
    }

    /**
     * Try every candidate pitch around the direct line, simulate each, and return the collision-free pitch
     * whose net displacement direction best matches the goal direction — verifying the goal stays visible
     * along the winning future (a great-scoring path that loses sight of the goal is flying behind a wall).
     */
    private Float sweepPitches(SolveSnapshot s, double dx, double dy, double dz, boolean desperate,
                               int ticks, int ticksBoosted, int boostDelay) {
        final double gx = dx - s.x(), gy = dy - s.y(), gz = dz - s.z();
        final double goalLen = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (goalLen < 0.5) return null;
        final double gnx = gx / goalLen, gny = gy / goalLen, gnz = gz / goalLen;
        final float goodPitch = (float) Math.toDegrees(Math.atan2(-gy, Math.hypot(gx, gz)));
        final float minPitch = desperate ? -88 : Math.max(goodPitch - s.pitchRange(), -88);
        final float maxPitch = desperate ? 88 : Math.min(goodPitch + s.pitchRange(), 88);

        Float bestPitch = null;
        double bestDot = -2;
        for (float pitch = goodPitch; pitch <= maxPitch; pitch++) {
            final double d = evalPitch(s, dx, dy, dz, pitch, ticks, ticksBoosted, boostDelay, gnx, gny, gnz, bestDot, desperate);
            if (d > bestDot) { bestDot = d; bestPitch = pitch; }
        }
        for (float pitch = goodPitch - 1; pitch >= minPitch; pitch--) {
            final double d = evalPitch(s, dx, dy, dz, pitch, ticks, ticksBoosted, boostDelay, gnx, gny, gnz, bestDot, desperate);
            if (d > bestDot) { bestDot = d; bestPitch = pitch; }
        }
        return bestPitch;
    }

    /** Score one pitch candidate: -2 (rejected) unless its simulated future is collision-free, beats the
     *  best so far, and keeps the goal visible. */
    private double evalPitch(SolveSnapshot s, double dx, double dy, double dz, float pitch,
                             int ticks, int ticksBoosted, int boostDelay,
                             double gnx, double gny, double gnz, double bestDot, boolean desperate) {
        final double[] path = simulate(s, dx, dy, dz, pitch, ticks, ticksBoosted, boostDelay);
        if (path == null) return -2;
        final int n = path.length / 3 - 1;
        final double ex = path[n * 3] - s.x(), ey = path[n * 3 + 1] - s.y(), ez = path[n * 3 + 2] - s.z();
        final double len = Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (len < 1e-6) return -2;
        final double dot = (ex * gnx + ey * gny + ez * gnz) / len;
        if (dot <= bestDot) return -2;                          // not an improvement — skip the expensive rays
        if (!simClear(path)) return -2;
        if (!goalVisibleAlong(path, dx, dy, dz, desperate)) return -2;
        return dot;
    }

    /**
     * Fly the exact vanilla elytra physics forward from the snapshot state: each tick the yaw re-aims at
     * what remains of the way to the aim point while the candidate pitch is held, mirroring how the real
     * control loop flies. Returns absolute positions (x,y,z per tick, index 0 = start), ending early once
     * within a block of the aim point; {@code null} when the future leaves the native cache's vertical range.
     */
    private double[] simulate(SolveSnapshot s, double dx, double dy, double dz, float pitch,
                              int ticks, int ticksBoosted, int boostDelay) {
        double px = s.x(), py = s.y(), pz = s.z();
        final double[] m = { s.vx(), s.vy(), s.vz() };
        final double[] out = new double[(ticks + 1) * 3];
        out[0] = px; out[1] = py; out[2] = pz;
        int n = 0;
        int boostLeft = ticksBoosted;
        for (int i = 0; i < ticks; i++) {
            final double ddx = dx - px, ddy = dy - py, ddz = dz - pz;
            if (ddx * ddx + ddy * ddy + ddz * ddz < 1) break;
            final float yaw = (float) Math.toDegrees(Math.atan2(-ddx, ddz));
            final Vector3d look = MathHelper.calculateViewVector(yaw, pitch);
            stepMotion(m, look.getX(), look.getY(), look.getZ(), pitch);
            px += m[0]; py += m[1]; pz += m[2];
            if (py >= 126 || py <= 1) return null;              // above the roof / into the floor — reject
            n++;
            out[n * 3] = px; out[n * 3 + 1] = py; out[n * 3 + 2] = pz;
            if (i >= boostDelay && boostLeft-- > 0) applyBoost(m, look.getX(), look.getY(), look.getZ());
        }
        if (n == 0) return null;
        return n == ticks ? out : Arrays.copyOf(out, (n + 1) * 3);
    }

    /** Sweep the gliding hitbox (0.6 wide, 0.6 tall) along every simulated tick — one batched native raytrace. */
    private boolean simClear(double[] path) {
        final int steps = path.length / 3 - 1;
        if (steps <= 0) return true;
        final double r = 0.31;                                  // half-width + a hair of padding
        final double[] off = {
            -r, -0.01, -r,  -r, -0.01, r,  r, -0.01, -r,  r, -0.01, r,
            -r, 0.61, -r,   -r, 0.61, r,   r, 0.61, -r,   r, 0.61, r,
        };
        final int count = steps * 8;
        final double[] src = new double[count * 3], dst = new double[count * 3];
        int k = 0;
        for (int i = 0; i < steps; i++) {
            for (int c = 0; c < 8; c++) {
                src[k]     = path[i * 3]           + off[c * 3];
                src[k + 1] = path[i * 3 + 1]       + off[c * 3 + 1];
                src[k + 2] = path[i * 3 + 2]       + off[c * 3 + 2];
                dst[k]     = path[(i + 1) * 3]     + off[c * 3];
                dst[k + 1] = path[(i + 1) * 3 + 1] + off[c * 3 + 1];
                dst[k + 2] = path[(i + 1) * 3 + 2] + off[c * 3 + 2];
                if (src[k] == dst[k] && src[k + 1] == dst[k + 1] && src[k + 2] == dst[k + 2]) {
                    dst[k + 1] += 1e-6;                         // zero-length rays crash the cpp tracer
                }
                k += 3;
            }
        }
        return NetherRouter.INSTANCE.allClear(count, src, dst);
    }

    /** The aim point must stay visible from (each step of / the end of) the simulated future. */
    private boolean goalVisibleAlong(double[] path, double dx, double dy, double dz, boolean finalOnly) {
        final int steps = path.length / 3 - 1;
        if (steps <= 0) return true;
        if (finalOnly) return rayClear(path[steps * 3], path[steps * 3 + 1], path[steps * 3 + 2], dx, dy, dz);
        final double[] src = new double[steps * 3], dst = new double[steps * 3];
        for (int i = 1; i <= steps; i++) {
            final int k = (i - 1) * 3;
            src[k] = path[i * 3]; src[k + 1] = path[i * 3 + 1]; src[k + 2] = path[i * 3 + 2];
            dst[k] = dx; dst[k + 1] = dy; dst[k + 2] = dz;
            if (src[k] == dst[k] && src[k + 1] == dst[k + 1] && src[k + 2] == dst[k + 2]) dst[k + 1] += 1e-6;
        }
        return NetherRouter.INSTANCE.allClear(steps, src, dst);
    }

    /** Screen a candidate aim point: all 8 hitbox corners (inflated by {@code margin}) must reach it clear. */
    private boolean hitboxClear(SolveSnapshot s, double dx, double dy, double dz, double margin) {
        final double r = 0.3 + margin;
        final double h0 = -margin, h1 = 0.6 + margin;
        final double[] off = { -r, h0, -r,  -r, h0, r,  r, h0, -r,  r, h0, r,
                               -r, h1, -r,  -r, h1, r,  r, h1, -r,  r, h1, r };
        final double ox = dx - s.x(), oy = dy - s.y(), oz = dz - s.z();
        final double[] src = new double[24], dst = new double[24];
        for (int c = 0; c < 8; c++) {
            src[c * 3]     = s.x() + off[c * 3];
            src[c * 3 + 1] = s.y() + off[c * 3 + 1];
            src[c * 3 + 2] = s.z() + off[c * 3 + 2];
            dst[c * 3]     = src[c * 3] + ox;
            dst[c * 3 + 1] = src[c * 3 + 1] + oy;
            dst[c * 3 + 2] = src[c * 3 + 2] + oz;
        }
        return NetherRouter.INSTANCE.allClear(8, src, dst);
    }

    /** Single native raytrace (solver thread; blocks on the cache lock). True = clear. */
    private boolean rayClear(double x0, double y0, double z0, double x1, double y1, double z1) {
        if (x0 == x1 && y0 == y1 && z0 == z1) return true;
        return NetherRouter.INSTANCE.allClear(1, new double[]{x0, y0, z0}, new double[]{x1, y1, z1});
    }

    /**
     * Tick-thread LOS for aim selection: the native cache when it's free (sees seed-generated terrain beyond
     * what the server has sent; unknown chunks = blocked), else the loaded-chunk scan (unloaded = clear).
     */
    private boolean flightLineClear(double x0, double y0, double z0, double x1, double y1, double z1) {
        if (y0 > 1 && y0 < 126 && y1 > 1 && y1 < 126) {
            final Boolean nv = NetherRouter.INSTANCE.tryIsVisible(x0, y0, z0, x1, y1, z1);
            if (nv != null) return nv;
        }
        return losClear(x0, y0, z0, x1, y1, z1);
    }

    /**
     * One tick of vanilla elytra glide physics ({@code travelFallFlying}), the Baritone port: mutates the
     * motion vector in place given the unit look direction and pitch. The only term dropped is
     * {@code min(1, |look|/0.4)}, which is always 1 for a unit look vector.
     */
    private static void stepMotion(double[] m, double lx, double ly, double lz, float pitchDeg) {
        double mx = m[0], my = m[1], mz = m[2];
        final double pitchRad = Math.toRadians(pitchDeg);
        final double flatLook = Math.sqrt(lx * lx + lz * lz);
        final double flatMotion = Math.sqrt(mx * mx + mz * mz);
        double cosPitch = Math.cos(pitchRad);
        cosPitch = cosPitch * cosPitch;
        my += -0.08 + cosPitch * 0.06;
        if (my < 0 && flatLook > 0) {                           // falling: the wings convert sink into lift
            final double lift = my * -0.1 * cosPitch;
            my += lift;
            mx += lx * lift / flatLook;
            mz += lz * lift / flatLook;
        }
        if (pitchRad < 0 && flatLook > 0) {                     // nose up: trade speed for height
            final double pull = flatMotion * -Math.sin(pitchRad) * 0.04;
            my += pull * 3.2;
            mx -= lx * pull / flatLook;
            mz -= lz * pull / flatLook;
        }
        if (flatLook > 0) {                                     // align flat motion with the look direction
            mx += (lx / flatLook * flatMotion - mx) * 0.1;
            mz += (lz / flatLook * flatMotion - mz) * 0.1;
        }
        m[0] = mx * 0.99;
        m[1] = my * 0.98;
        m[2] = mz * 0.99;
    }

    /** One tick of firework rocket thrust on the motion vector (vanilla FireworkRocketEntity). */
    private static void applyBoost(double[] m, double lx, double ly, double lz) {
        m[0] += lx * 0.1 + (lx * 1.5 - m[0]) * 0.5;
        m[1] += ly * 0.1 + (ly * 1.5 - m[1]) * 0.5;
        m[2] += lz * 0.1 + (lz * 1.5 - m[2]) * 0.5;
    }

    // ========================== end simulation flight solver ==========================

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

        // Don't outrun chunk loading (see tickNetherCruise) — but highways sit in previously-loaded, often-reloaded
        // chunks, so loading is ~30% less of a problem here: gentler thresholds. The bounce has no firework boost, so
        // the brake is the jump hold — release it to stop bouncing and let the glide bleed speed; right at the frontier,
        // also skip the redeploy so the bot settles toward a stop and waits for chunks to stream in.
        int slowDist = Math.round(cfg.netherFrontierSlow * HIGHWAY_FRONTIER_FACTOR);
        int holdDist = Math.round(cfg.netherFrontierHold * HIGHWAY_FRONTIER_FACTOR);
        int corridor = loadedDistAhead(x, z, yaw, slowDist);
        boolean frontierCoast = corridor < slowDist;
        boolean frontierHold  = corridor <= holdDist;

        boolean overCap = speed * 20.0 >= cfg.maxSpeed; // 2b2t ~40 b/s limit — coast (no bounce) to bleed speed
        if (!BOT.isFallFlying() && !overCap && !frontierHold && redeployCooldown <= 0) {
            sendStartFallFlying();                       // re-engage the elytra at the bottom of each bounce
            redeployCooldown = cfg.bounceRedeployTicks;
        }
        boolean jump = !overCap && !frontierCoast;       // held jump auto-jumps on each ground touch
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
        boolean fire = fireSpaced(!overCap) && heldIsFirework();
        if (!fire) ensureFireworkHeld();
        if (fire) noteRocketFired();
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
            if (fire) noteRocketFired();
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
        wantFire = fireSpaced(wantFire);
        boolean fire = wantFire && heldIsFirework();
        if (wantFire && !fire) ensureFireworkHeld();
        if (fire) noteRocketFired();
        submitInput(false, fire, yaw, pitch);
    }

    /**
     * Helicopter landing, decoded from a Baritone elytra packet capture: brake horizontal speed to near zero
     * (nose up), then flutter near-level with a continuous yaw spin — the deployed elytra sinks ~3 b/s almost
     * vertically onto the spot, and the spin cancels any residual drift. Replaces the old 45° dive-in, which
     * built speed and overshot. The elytra stays deployed the whole way down (the capture sent zero re-deploys).
     */
    private void tickLand(double x, double y, double z, double speed) {
        var cfg = CONFIG.client.extra.elytraPilot;
        if (!BOT.isFallFlying()) {
            double horiz = horizDist(x, z, cfg.targetX + 0.5, cfg.targetZ + 0.5);
            if (cfg.baritoneLand && cfg.hasTarget && horiz > cfg.arriveRadius) { enterLandWalk(); return; }
            complete("landed");
            return;
        }
        if (landTicks == 0) landSpinYaw = CACHE.getPlayerCache().getYaw();
        double horizSpot = haveLandSpot ? horizDist(x, z, landX + 0.5, landZ + 0.5) : 0;
        if (horizSpot > cfg.arriveRadius * 3 + 8) { phase = Phase.DESCEND; return; }  // drifted off — re-approach
        if (heightAboveGround(x, y, z) <= cfg.landCutClearance) BOT.stopFallFlying(); // low over the spot — drop in
        landSpinYaw += cfg.landSpinStep;
        if (landSpinYaw > 180f) landSpinYaw -= 360f;
        float pitch = speed * 20.0 > cfg.landBrakeSpeed
            ? -55f   // still moving — nose up to bleed speed (trades the last momentum for a little height)
            : -3f;   // near-stationary — flutter slightly nose-up; the elytra sinks straight down
        submitInput(false, false, landSpinYaw, pitch);
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
     * Walk-out recovery: flying is not working HERE (terrain pocket, overhang, pin), so do what a human does —
     * settle, let Baritone ground-path toward the target, and take off again once there's open sky overhead.
     * Bounded legs and attempts; aborts when walking out repeatedly fails too.
     */
    private void enterWalkout(String why) {
        var pc = CACHE.getPlayerCache();
        // Chimney-trap breaker: repeated walkouts from ~the same spot mean the "cleared" spot isn't actually
        // flyable (vertical clearance, walls all around). Force the next leg to get DISTANCE before retakeoff.
        if (horizDist(pc.getX(), pc.getZ(), loopX, loopZ) < 12) {
            loopCount++;
        } else {
            loopCount = 1;
            loopX = pc.getX();
            loopZ = pc.getZ();
        }
        walkoutFar = loopCount >= 3;
        if (walkoutFar) warn("Takeoff keeps failing around {}, {} — walking far before retrying", (int) loopX, (int) loopZ);
        phase = Phase.WALKOUT;
        baritoneStarted = false;
        walkoutTicks = 0;
        BARITONE.stop();
        inGameAlertActivePlayer("<yellow>ElytraPilot: " + why + " — walking toward open ground to retry");
        warn("{} — walking out (attempt {}/{})", why, walkoutAttempts + 1, MAX_WALKOUT_ATTEMPTS);
    }

    private void tickWalkout(double x, double y, double z) {
        if (!baritoneStarted) {
            double[] u = unitToTarget(x, z);
            // Rotate the flee direction each failed leg — re-walking the same blocked line burned all 4
            // attempts in seconds when lava surrounded the first heading.
            double ang = switch (walkoutAttempts % 4) {
                case 1  -> Math.toRadians(50);
                case 2  -> Math.toRadians(-50);
                case 3  -> Math.toRadians(110);
                default -> 0;
            };
            if (ang != 0) {
                double cos = Math.cos(ang), sin = Math.sin(ang);
                u = new double[]{ u[0] * cos - u[1] * sin, u[0] * sin + u[1] * cos };
            }
            int wx = MathHelper.floorI(x + u[0] * WALKOUT_LEG_BLOCKS);
            int wz = MathHelper.floorI(z + u[1] * WALKOUT_LEG_BLOCKS);
            // Aim the leg into the walkable mid-band: from a roof perch the way out is DOWN, from the
            // lava-sea level it is UP — walking 48 blocks at the bot's own Y is hopeless in both.
            int wy = Math.min(Math.max(MathHelper.floorI(y), 50), 95);
            BARITONE.pathTo(new GoalNear(wx, wy, wz, 12));
            baritoneStarted = true;
            walkoutTicks = 0;
            return;
        }
        walkoutTicks++;
        // Baritone is driving — no flight inputs. Once the spot is genuinely flyable, hand back to a fresh
        // takeoff: open sky ABOVE plus lateral room toward the target (a chimney passes a column-only check),
        // and — after repeated same-spot failures — real distance from the trap.
        if (walkoutTicks > 20 && !BOT.isFallFlying()
                && clearAboveCount(x, y, z) >= WALKOUT_OPEN_SKY
                && clearDistAhead(x, y + 2, z, desiredYaw(x, z), 8) >= 8
                && (!walkoutFar || horizDist(x, z, loopX, loopZ) > 24)) {
            BARITONE.stop();
            info("Walked clear (open sky above at {}, {}, {}) — taking off again", (int) x, (int) y, (int) z);
            lostEpisodes = 0;
            lostFlightTicks = 0;
            lostLogged = false;
            healthyTicks = 0;
            pinTicks = 0;
            takeoffTicks = 0;
            jumpToggle = false;
            baritoneStarted = false;
            phase = Phase.TAKEOFF;
            return;
        }
        // Give Baritone time to compute + start before judging the leg failed (instant `!isActive` on a
        // blocked heading used to burn every attempt within seconds).
        if ((walkoutTicks > 60 && !BARITONE.isActive()) || walkoutTicks > WALKOUT_TIMEOUT_TICKS) {
            baritoneStarted = false;  // leg ended without open sky — try a different direction
            if (++walkoutAttempts >= MAX_WALKOUT_ATTEMPTS)
                abort("could not walk clear of the terrain (after " + walkoutAttempts + " walk-out legs)");
        }
    }

    /** Unit XZ direction from here toward the configured target. */
    private double[] unitToTarget(double x, double z) {
        var cfg = CONFIG.client.extra.elytraPilot;
        double dx = cfg.targetX - x, dz = cfg.targetZ - z;
        double len = Math.hypot(dx, dz);
        if (len < 1) return new double[]{ 0, 1 };
        return new double[]{ dx / len, dz / len };
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
    /**
     * Blocks forward (along {@code yaw}) before the first UNLOADED chunk — the bot's effective "headlight" range. On
     * 2b2t only ~12 chunks load and they stream in slowly, so when flying fast this corridor is the real limit on how
     * far the bot can safely commit. Returns {@code look} if everything out to that distance is loaded.
     */
    private int loadedDistAhead(double x, double z, float yaw, int look) {
        double r = Math.toRadians(yaw);
        double lx = -Math.sin(r), lz = Math.cos(r);
        for (int d = 2; d <= look; d += 2) {
            int bx = MathHelper.floorI(x + lx * d);
            int bz = MathHelper.floorI(z + lz * d);
            if (!World.isChunkLoadedChunkPos(bx >> 4, bz >> 4)) return d;
        }
        return look;
    }

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
        if (!BOT.isFallFlying() && heightAboveGround(x, y, z) <= 2) complete("emergency landing complete", false);
    }

    /**
     * Every totem pop is a LETHAL hit the bot just barely survived. More than the configured limit during one
     * flight means the situation is beyond what the pilot can fly out of (a lava pin killed the bot through 6
     * totems in 6 seconds) — abort, and optionally LOG OUT to preserve the bot and its kit where it stands.
     */
    private void onTotemPop(TotemPopEvent e) {
        if (e.entityId() != CACHE.getPlayerCache().getEntityId()) return;
        if (phase == Phase.IDLE || phase == Phase.DONE) return;
        var cfg = CONFIG.client.extra.elytraPilot;
        if (!cfg.totemPopAbort) return;
        totemPops++;
        if (totemPops <= cfg.totemPopLimit) {
            warn("Totem popped in flight ({}/{} before abort)", totemPops, cfg.totemPopLimit);
            return;
        }
        warn("Popped {} totems — aborting the flight{}", totemPops,
            cfg.totemPopLogout ? " and DISCONNECTING to preserve the bot" : "");
        abort("popped " + totemPops + " totems");
        if (cfg.tripActive) {                       // kill the trip too, so nothing re-arms on reconnect
            cfg.tripActive = false;
            MODULE.get(ElytraTrip.class).syncEnabledFromConfig();
        }
        if (cfg.totemPopLogout) {
            Proxy.getInstance().disconnect("ElytraPilot: popped " + totemPops + " totems — emergency logout");
        }
    }

    /**
     * Called from CRUISE/DESCEND when the bot is unexpectedly not fall-flying. Bounded: after
     * {@code stallRecoverTicks} of failed re-deploys it escalates to a fresh ground TAKEOFF (whose own timeout
     * aborts the flight), and after {@code maxGroundRecoveries} episodes it aborts outright — the first live
     * nether trip died in a silent 2.5-minute redeploy-vs-server fight on the ground; never again.
     */
    private void handleLostFlight(double x, double y, double z) {
        var cfg = CONFIG.client.extra.elytraPilot;
        // Taking damage while grounded (lava/fire/mobs) — do NOT stand here methodically redeploying; that
        // burned 11 totems in 11 seconds and killed the bot. Flee NOW (Baritone paths around lava).
        if (hpDropped && cfg.hasTarget && walkoutAttempts < MAX_WALKOUT_ATTEMPTS) {
            enterWalkout("taking damage while grounded at " + (int) x + ", " + (int) y + ", " + (int) z);
            return;
        }
        if (!lostLogged) {
            lostLogged = true;
            if (++lostEpisodes > cfg.maxGroundRecoveries) {
                // Rapid-fire flight loss = this spot is unflyable (pocket/overhang) — redeploying here can never
                // work. Walk toward open ground and retry (bounded), instead of giving up in place.
                if (cfg.hasTarget && walkoutAttempts < MAX_WALKOUT_ATTEMPTS) {
                    enterWalkout("kept losing flight at " + (int) x + ", " + (int) y + ", " + (int) z);
                } else {
                    abort("kept losing flight (" + lostEpisodes + " episodes in quick succession) at "
                        + (int) x + ", " + (int) y + ", " + (int) z);
                }
                return;
            }
            warn("Lost flight at {}, {}, {} (episode {}/{}) — redeploying",
                (int) x, (int) y, (int) z, lostEpisodes, cfg.maxGroundRecoveries);
        }
        if (++lostFlightTicks > cfg.stallRecoverTicks) {
            warn("Could not re-deploy in {} ticks — attempting a fresh ground takeoff", cfg.stallRecoverTicks);
            inGameAlertActivePlayer("<yellow>ElytraPilot: grounded mid-flight — attempting a fresh takeoff");
            lostFlightTicks = 0;
            takeoffTicks = 0;
            jumpToggle = false;
            phase = Phase.TAKEOFF;   // its own timeout aborts ("could not take off") if this fails too
            return;
        }
        if (wornElytraDurability() > 2) {            // still wearing a working elytra — just re-deploy
            jumpToggle = !jumpToggle;
            boolean fire = BOT.isFallFlying() && heldIsFirework();
            submitInput(jumpToggle, fire, desiredYaw(x, z), 0f);
            if (fire) noteRocketFired();
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

    /**
     * Straight-line clearance check through LOADED chunks only — unloaded chunks count as clear, because the
     * bot can only ever collide with loaded terrain (the native route handles the unloaded world). Steps the
     * segment at ~1-block intervals; lava counts as blocked (never aim a flight line through it).
     */
    private boolean losClear(double x0, double y0, double z0, double x1, double y1, double z1) {
        final double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        final int steps = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz));
        if (steps <= 0) return true;
        for (int i = 1; i <= steps; i++) {
            final double t = (double) i / steps;
            final int bx = MathHelper.floorI(x0 + dx * t);
            final int by = MathHelper.floorI(y0 + dy * t);
            final int bz = MathHelper.floorI(z0 + dz * t);
            if (!World.isChunkLoadedChunkPos(bx >> 4, bz >> 4)) continue;
            final var b = World.getBlock(bx, by, bz);
            if (!b.isAir() && !World.isWater(b)) return false;
        }
        return true;
    }

    /** True if the first non-air, non-water block below the bot (within a cap) is lava — i.e. flying over a lava sea. */
    private boolean lavaBelow(double x, double y, double z) {
        int bx = MathHelper.floorI(x), bz = MathHelper.floorI(z);
        if (!World.isChunkLoadedChunkPos(bx >> 4, bz >> 4)) return false;
        int fy = MathHelper.floorI(y);
        for (int dy = 1; dy <= 64; dy++) {
            int yy = fy - dy;
            if (yy < -64) break;
            var b = World.getBlock(bx, yy, bz);
            if (b.isAir() || World.isWater(b)) continue;
            return World.isFluid(b);   // first non-air/water block below: lava if it's a (non-water) fluid
        }
        return false;
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

    private static double wrapDeg(double d) {
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        return d;
    }

    private void complete(String why) {
        complete(why, true);
    }

    private void complete(String why, boolean success) {
        phase = Phase.DONE;
        lastFlightSuccess = success;
        BARITONE.stop();
        inGameAlertActivePlayer("<green>ElytraPilot: " + why);
        info("Flight complete: " + why);
    }

    private void abort(String why) {
        phase = Phase.DONE;
        lastFlightSuccess = false;
        BARITONE.stop();
        inGameAlertActivePlayer("<red>ElytraPilot aborted: " + why);
        warn("Aborted: " + why);
    }
}
