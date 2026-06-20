package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.event.client.ClientDeathEvent;
import com.aquarius.feature.elytra.Route;
import com.aquarius.feature.pathfinder.goals.GoalNear;
import com.aquarius.feature.player.World;
import com.aquarius.mc.block.BlockRegistry;
import com.aquarius.mc.dimension.DimensionRegistry;
import com.aquarius.module.api.Module;
import com.aquarius.util.config.Config.Client.Extra.ElytraPilot.HighwayDir;

import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.BARITONE;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

/**
 * ElytraTrip — high-level "trip planner" that drives {@link ElytraPilot} (and Baritone) across multiple legs and
 * dimensions to reach a far destination.
 *
 * <p>Routing decision, from the destination's distance to the BOT (not to spawn):
 * <ul>
 *   <li><b>Within {@code spawnRegionRadius}</b> of the bot (default 100k): fly the overworld straight to the target.</li>
 *   <li><b>Beyond it</b>: hop to the nether (8:1 scale makes the distance 8× cheaper), ride the nearest 2b2t
 *       nether highway toward the target's nether coords, exit through a portal near there, and fly the final
 *       overworld leg to the exact target.</li>
 * </ul>
 *
 * <p>It does not move the bot itself — each leg is delegated: flight legs to {@link ElytraPilot} (configured +
 * {@code beginFlight()}/{@code endFlight()}), and the portal entries to Baritone via
 * {@code BARITONE.getTo(NETHER_PORTAL)} (the same primitive {@link SpawnPatrol} uses). The planner only decides
 * which leg runs and when it's finished, so the two never fight for inputs. All config lives under
 * {@code CONFIG.client.extra.elytraPilot} (the {@code trip*} fields).
 *
 * <p>FIRST CUT — not yet live-tested. Known limits to tune on a real run: portal entry relies on a portal being
 * reachable (Baritone {@code getTo} only finds loaded ones), so the source needs a portal near the bot and the
 * destination needs a portal near its nether coords; the "fly 100k out to search for a portal" behaviour is not
 * yet implemented (it goes straight for the nearest reachable portal); and dimension-transition timing
 * (portal cooldown) may need grace ticks.
 */
public class ElytraTrip extends Module {

    private enum Phase {
        IDLE,
        GEAR_UP,           // naked at the start: Regear the flight kit from a nearby ender chest, then fly
        OW_DIRECT,         // destination within the spawn region: fly the overworld straight there
        ENTER_NETHER,      // walk into the nearest portal, wait for the nether
        NETHER_HW_FLYIN,   // highways: >acquireRadius from the chosen road — open-nether fly to its centerline first
        NETHER_HW_ACQUIRE, // highways: <=acquireRadius — Baritone onto the road centerline at roadY (climb/tunnel/center)
        NETHER_HIGHWAY,    // highways: on the road — e-bounce along the chosen highway toward the target's nether coords
        NETHER_OPEN,       // highways off — open-nether cruise straight to the target's nether coords
        NETHER_DEST,       // the destination IS in the nether: fly to the exact coords and land there
        NETHER_SEEK_EXIT,  // near the target's nether coords: walk into an exit portal, wait for the overworld
        FINAL_APPROACH,    // fly the overworld to the exact target
        DONE,
        FAILED
    }

    private static final double NETHER_SCALE = 8.0;
    private static final int THROTTLE_TICKS = 20;        // run the planner ~once a second
    private static final int PORTAL_GUARD_TICKS = 3600;  // ~3 min on a portal leg before giving up
    private static final int HIGHWAY_GUARD_TICKS = 24000;// ~20 min on a single highway leg before giving up
    private static final int GEAR_GUARD_TICKS = 3600;    // ~3 min for the pre-flight gear-up before giving up
    private static final int ACQUIRE_GUARD_TICKS = 6000; // ~5 min to Baritone onto the road before giving up
    private static final double HW_S = 0.7071067811865476; // 1/sqrt(2)
    private static final int HW_CENTER_TOL = 3;          // "on the road": within this perp distance + |y-roadY| to count as acquired
    private static final int HW_ACQUIRE_RANGE_SQ = 4;    // Baritone GoalNear range² onto the centerline (within ~2 blocks)

    private Phase phase = Phase.IDLE;
    private int tickCtr;
    private int guardTicks;
    private int graceTicks;   // post-portal wait before starting a nether flight leg (chunks stream in first)
    private boolean legStarted;
    private boolean gearStarted;      // GEAR_UP: have we kicked off Regear yet
    private boolean savedEquipElytra; // GEAR_UP: prior regear.equipElytra, restored when gear-up ends
    private HighwayDir chosenDir;     // nether transit: the highway picked (nearest line that heads toward the target)

    // --- multi-leg route execution ---
    private Route activeRoute;        // non-null while running a saved route; null = a plain coord trip
    private int legIndex;             // index of the leg currently being flown/ridden
    private double objX, objZ;        // current nether objective (where this leg/transit is heading), nether coords
    private double lineAx, lineAz;    // current e-bounce road line: anchor point (nether coords)
    private double lineDx, lineDz;    // current e-bounce road line: unit direction

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.elytraPilot.tripActive;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(ClientBotTick.Starting.class, e -> {
                // Re-arm an armed trip when the bot (re)enters the world: a trip set while logged out — or
                // interrupted by a disconnect — begins/resumes on login. Toggleable; off → a connect leaves it idle.
                if (CONFIG.client.extra.elytraPilot.tripActive && CONFIG.client.extra.elytraPilot.tripStartOnConnect) {
                    info("Bot entered the world with an armed trip — starting it.");
                    armTrip();
                } else { phase = Phase.IDLE; legStarted = false; }
            }),
            of(ClientBotTick.Stopped.class, e -> { phase = Phase.IDLE; legStarted = false; }),
            of(ClientDeathEvent.class, e -> {
                // A death invalidates everything (gear dropped, respawned at bed/spawn) — never march on.
                // EXCEPTION: during gear-up with self-kill relocation, deaths are intentional (escaping a boxed-in
                // 2b2t spawn) and the bot has no gear yet to lose — let Regear keep relocating, don't cancel.
                if (phase == Phase.GEAR_UP && CONFIG.client.extra.regear.selfKillRelocate) return;
                if (phase != Phase.IDLE && phase != Phase.DONE && phase != Phase.FAILED)
                    abort("bot died — trip cancelled (it respawned at its bed/spawn; gear is at the death point)");
            })
        );
    }

    @Override
    public void onEnable() {
        armTrip();
    }

    /** Initialise the trip: gear up first if the pre-flight checklist isn't satisfied, else pick the first leg.
     *  Called on enable (Launch / {@code /fly trip}) and again on world-enter when {@code tripStartOnConnect}. */
    private void armTrip() {
        var cfg = CONFIG.client.extra.elytraPilot;
        guardTicks = 0;
        graceTicks = 0;
        legStarted = false;
        gearStarted = false;
        // A saved route is armed? Run it instead of the coord trip.
        if (!cfg.tripActiveRoute.isBlank()) {
            Route r = cfg.tripRoutes.get(cfg.tripActiveRoute);
            if (r != null) { armRoute(r); return; }
            warn("Armed route '{}' not found — falling back to a coord trip.", cfg.tripActiveRoute);
            cfg.tripActiveRoute = "";
        }
        activeRoute = null;
        resetCustomLine();                 // clear any stale road line from a prior route
        // Pre-flight checklist not satisfied? Gear up (Regear tops up the deficits from the echest) first.
        if (cfg.tripGearUp && !FlightGear.ready()) {
            phase = Phase.GEAR_UP;
            info("Trip: pre-flight check not satisfied — gearing up first.\n{}", FlightGear.report());
            return;
        }
        decideStartPhase();
    }

    /** Arm a saved multi-leg route: gear up if needed, then enter the nether (if not already) and run leg 0. */
    private void armRoute(Route r) {
        var cfg = CONFIG.client.extra.elytraPilot;
        activeRoute = r;
        legIndex = 0;
        guardTicks = 0; graceTicks = 0; legStarted = false; gearStarted = false; chosenDir = null;
        resetCustomLine();
        // The route's own legs handle the nether transit; the final OW approach + displays use tripTarget.
        cfg.tripTargetX = r.destX(); cfg.tripTargetY = r.destY(); cfg.tripTargetZ = r.destZ();
        cfg.tripTargetIsNether = false;
        if (r.legs().isEmpty()) { abort("route '" + r.id() + "' has no legs — add some with `fly trip route leg`"); return; }
        info("Route '{}': {} leg(s), ending {}.", r.id(), r.legs().size(),
            r.endInNether() ? "in the nether" : "overworld via a portal");
        if (cfg.tripGearUp && !FlightGear.ready()) {
            phase = Phase.GEAR_UP;
            info("Route: pre-flight check not satisfied — gearing up first.\n{}", FlightGear.report());
            return;
        }
        decideRouteStart();
    }

    /** Choose the first flight/portal leg from the destination (called at start, and after a gear-up). */
    private void decideStartPhase() {
        if (activeRoute != null) { decideRouteStart(); return; }   // a route's first leg, not a coord trip
        var cfg = CONFIG.client.extra.elytraPilot;
        guardTicks = 0;
        graceTicks = 0;
        legStarted = false;
        chosenDir = null;
        if (cfg.tripTargetIsNether) {
            // tripTargetX/Z are NETHER coords and the trip ENDS in the nether — no exit portal, no exit radius.
            phase = inNether() ? Phase.NETHER_DEST : Phase.ENTER_NETHER;
            info("Trip: nether destination {}, {} — {}.", cfg.tripTargetX, cfg.tripTargetZ,
                inNether() ? "flying there" : "entering the nearest portal first");
            return;
        }
        // Overworld-direct vs nether routing keys off how far the BOT is from the target — NOT the target's
        // distance from spawn. A bot already parked out at a deep base must fly the short hop to a nearby target,
        // not route millions of blocks back through the nether from 0,0. Only an overworld leg flies direct, so a
        // bot already in the nether always routes via the nether.
        double dist = FlightGear.directLegDistance();          // bot→target, overworld blocks
        if (inOverworld() && dist <= cfg.spawnRegionRadius) {
            phase = Phase.OW_DIRECT;
            info("Trip: {}, {} is {}b from the bot (within the {}b direct radius) — flying overworld-direct.",
                cfg.tripTargetX, cfg.tripTargetZ, (long) dist, cfg.spawnRegionRadius);
        } else {
            phase = Phase.ENTER_NETHER;
            info("Trip: {}, {} — routing via the nether (nether target {}, {}).",
                cfg.tripTargetX, cfg.tripTargetZ,
                Math.round(cfg.tripTargetX / NETHER_SCALE), Math.round(cfg.tripTargetZ / NETHER_SCALE));
        }
    }

    /** Start a route: be in the nether (enter a portal if not), then run leg 0. */
    private void decideRouteStart() {
        guardTicks = 0; graceTicks = 0; legStarted = false;
        if (inNether()) {
            beginLeg(0);
        } else {
            phase = Phase.ENTER_NETHER;
            info("Route '{}': entering the nether first.", activeRoute.id());
        }
    }

    @Override
    public void onDisable() {
        phase = Phase.IDLE;
        legStarted = false;
        if (gearStarted) {                       // cancelled mid gear-up: stop Regear + restore its config
            gearStarted = false;
            restoreRegearConfig();
            CONFIG.client.extra.regear.enabled = false;
            MODULE.get(Regear.class).syncEnabledFromConfig();
        }
        elytra().endFlight();
        BARITONE.stop();
        clearRouteState();   // a genuine disable (e.g. `trip off`) clears the armed route + custom road line
    }

    private void onTick(final ClientBotTick event) {
        if (phase == Phase.IDLE || phase == Phase.DONE || phase == Phase.FAILED) return;
        if (++tickCtr % THROTTLE_TICKS != 0) return; // ~1 Hz planner; the active leg runs every tick on its own
        try {
            var cfg = CONFIG.client.extra.elytraPilot;
            switch (phase) {
                case GEAR_UP          -> tickGearUp();
                case OW_DIRECT        -> tickOwDirect(cfg.tripTargetX, cfg.tripTargetZ);
                case ENTER_NETHER     -> tickEnterNether();
                case NETHER_HW_FLYIN  -> tickNetherHwFlyin(cfg);
                case NETHER_HW_ACQUIRE -> tickNetherHwAcquire(cfg);
                case NETHER_HIGHWAY   -> tickNetherHighway(cfg);
                case NETHER_OPEN      -> tickNetherOpen(cfg);
                case NETHER_DEST      -> tickNetherDest(cfg);
                case NETHER_SEEK_EXIT -> tickNetherSeekExit();
                case FINAL_APPROACH   -> tickFinalApproach(cfg.tripTargetX, cfg.tripTargetZ);
                default               -> { }
            }
        } catch (final Exception e) {
            error("Trip planner error", e);
            abort("internal error");
        }
    }

    // --- legs ---

    /**
     * Pre-flight gear-up: the bot has no elytra worn, so run Regear to pull the flight kit from a nearby ender
     * chest (forcing the elytra into the chest slot), wait until it's geared (elytra worn + fireworks in
     * inventory), then hand off to the normal flight leg. Aborts the trip if Regear can't kit up.
     */
    private void tickGearUp() {
        var rg = MODULE.get(Regear.class);
        if (FlightGear.ready()) {                          // checklist satisfied — go fly
            if (gearStarted) { restoreRegearConfig(); gearStarted = false; }
            info("Pre-flight check passed — starting the trip.");
            decideStartPhase();
            return;
        }
        if (!gearStarted) {                                // top up the deficits from the echest (refill mode)
            savedEquipElytra = CONFIG.client.extra.regear.equipElytra;
            CONFIG.client.extra.regear.equipElytra = true;
            CONFIG.client.extra.regear.enabled = true;
            rg.setFlightRefill(true);                      // pull ONLY what the checklist is missing
            rg.syncEnabledFromConfig();
            gearStarted = true;
            guardTicks = 0;
            info("Pre-flight: gear missing — running Regear to top up from the ender chest.\n{}", FlightGear.report());
            return;
        }
        if (rg.isPaused()) {                               // Regear couldn't kit up (no echest / kit shulker)
            restoreRegearConfig(); gearStarted = false;
            abort("gear-up failed — Regear could not kit up (check the ender chest + a kit shulker named per `regear name`).\n" + FlightGear.report());
            return;
        }
        if (rg.isComplete()) {                             // Regear finished — re-check the full checklist
            restoreRegearConfig(); gearStarted = false;
            if (FlightGear.ready()) decideStartPhase();
            else abort("gear-up finished but the kit was short. Still missing:\n" + FlightGear.report());
            return;
        }
        if (rg.isRelocating()) { guardTicks = 0; return; }   // self-kill relocation in progress — don't time it out
        if ((guardTicks += THROTTLE_TICKS) > GEAR_GUARD_TICKS) {
            restoreRegearConfig(); gearStarted = false;
            abort("gear-up timed out");
        }
    }

    private void restoreRegearConfig() {
        CONFIG.client.extra.regear.equipElytra = savedEquipElytra;
        MODULE.get(Regear.class).setFlightRefill(false);
    }

    private void tickOwDirect(int tx, int tz) {
        if (!legStarted) { startFlightTo(tx, tz); return; }
        if (elytra().isFlightDone()) {
            if (elytra().wasFlightSuccessful()) finish("arrived (overworld-direct)");
            else abort("the flight leg failed before reaching the destination");
        }
    }

    private void tickEnterNether() {
        if (inNether()) {
            var cfg = CONFIG.client.extra.elytraPilot;
            if (activeRoute != null) {                  // running a saved route — start its first leg
                info("In the nether — starting route '{}'.", activeRoute.id());
                beginLeg(legIndex);
                return;
            }
            if (cfg.tripTargetIsNether) {
                info("In the nether — preparing the flight to the destination.");
                phase = Phase.NETHER_DEST;
            } else if (cfg.tripUseHighways) {
                objX = cfg.tripTargetX / NETHER_SCALE;
                objZ = cfg.tripTargetZ / NETHER_SCALE;
                chosenDir = chooseHighway(objX, objZ);  // the road the destination lies nearest to
                double[] u = unit(chosenDir);
                setLine(0, 0, u[0], u[1]);              // spawn highway: line through origin
                cfg.roadY = 120;
                info("In the nether — riding the {} highway toward the target.", chosenDir);
                phase = Phase.NETHER_HW_FLYIN;
            } else {
                objX = cfg.tripTargetX / NETHER_SCALE;
                objZ = cfg.tripTargetZ / NETHER_SCALE;
                info("In the nether — cruising open-nether toward the target.");
                phase = Phase.NETHER_OPEN;
            }
            legStarted = false;
            guardTicks = 0;
            graceTicks = 0;
            return;
        }
        elytra().endFlight();                                           // don't let flight fight Baritone
        if (!BARITONE.isActive()) BARITONE.getTo(BlockRegistry.NETHER_PORTAL); // walk into the nearest portal
        if ((guardTicks += THROTTLE_TICKS) > PORTAL_GUARD_TICKS)
            abort("no reachable nether portal — move the bot near a portal (or build one) and retry");
    }

    /**
     * Road transit, step 1 — fly IN to the road. After the post-portal grace, measure the bot's perpendicular
     * distance to the current road line (set by the caller: a spawn highway for a coord trip, or the leg's
     * prev→endpoint line for a route). Within {@code highwayAcquireRadius} we hand straight to Baritone acquisition
     * (no flight); beyond it we open-nether cruise to the centerline foot first — so the bot only ever "nether
     * flies" when it's outside the road's acquisition range.
     */
    private void tickNetherHwFlyin(com.aquarius.util.config.Config.Client.Extra.ElytraPilot cfg) {
        if (!inNether()) { elytra().endFlight(); phase = Phase.FINAL_APPROACH; legStarted = false; return; }
        // Post-portal grace before any movement (let the first chunks stream in); only while we haven't taken off.
        if (!legStarted && (graceTicks += THROTTLE_TICKS) < cfg.tripNetherEntryGraceTicks) return;

        double cx = CACHE.getPlayerCache().getX(), cz = CACHE.getPlayerCache().getZ();
        double perp = perpDist(cx, cz);
        if (perp <= cfg.highwayAcquireRadius) {            // close enough — Baritone straight onto the road
            elytra().endFlight();
            info("Within {}b of the road — walking onto it.", (long) perp);
            phase = Phase.NETHER_HW_ACQUIRE; legStarted = false; guardTicks = 0;
            return;
        }
        if (!legStarted) {                                  // open-nether cruise to the centerline foot
            double t = Math.max(0, footT(cx, cz));          // clamp ≥0 so we never aim back behind the anchor
            cfg.highway = false; cfg.ebounce = false; cfg.hasTarget = true;
            cfg.targetX = (int) Math.round(lineAx + t * lineDx);
            cfg.targetZ = (int) Math.round(lineAz + t * lineDz);
            info("Flying open-nether to the road centerline at {}, {} ({}b away).", cfg.targetX, cfg.targetZ, (long) perp);
            elytra().beginFlight();
            legStarted = true;
        }
        if (elytra().isFlightDone()) {                      // landed (or failed) short of the road — acquire from here
            elytra().endFlight();
            phase = Phase.NETHER_HW_ACQUIRE; legStarted = false; guardTicks = 0;
            return;
        }
        if ((guardTicks += THROTTLE_TICKS) > HIGHWAY_GUARD_TICKS)
            abort("nether fly-in to the road timed out");
    }

    /**
     * Road transit, step 2 — Baritone ONTO the road. Path to the centerline foot at {@code roadY}: Baritone pillars
     * up if the bot is below, tunnels/bridges across to center on the 4-6-wide road. Done once the bot is within
     * {@link #HW_CENTER_TOL} of both the centerline and road level, then hands off to the bounce leg.
     */
    private void tickNetherHwAcquire(com.aquarius.util.config.Config.Client.Extra.ElytraPilot cfg) {
        if (!inNether()) { elytra().endFlight(); phase = Phase.FINAL_APPROACH; legStarted = false; return; }
        double cx = CACHE.getPlayerCache().getX(), cy = CACHE.getPlayerCache().getY(), cz = CACHE.getPlayerCache().getZ();
        double perp = perpDist(cx, cz);
        if (perp <= HW_CENTER_TOL && Math.abs(cy - cfg.roadY) <= HW_CENTER_TOL) {  // on the road centerline at road level
            BARITONE.stop();
            info("On the road centerline at y{} — bouncing toward the target.", cfg.roadY);
            phase = Phase.NETHER_HIGHWAY; legStarted = false; guardTicks = 0; graceTicks = 0;
            return;
        }
        elytra().endFlight();                               // don't let flight fight Baritone
        if (!BARITONE.isActive()) {
            double t = Math.max(0, footT(cx, cz));          // perpendicular foot of the bot's along-road position
            BARITONE.pathTo(new GoalNear((int) Math.round(lineAx + t * lineDx), cfg.roadY,
                (int) Math.round(lineAz + t * lineDz), HW_ACQUIRE_RANGE_SQ));
        }
        if ((guardTicks += THROTTLE_TICKS) > ACQUIRE_GUARD_TICKS)
            abort("couldn't path onto the road (walled in / lava / no route) — move the bot near the road and retry");
    }

    /** Road transit, step 3 — e-bounce along the current road line toward the objective until the exit radius. */
    private void tickNetherHighway(com.aquarius.util.config.Config.Client.Extra.ElytraPilot cfg) {
        if (!inNether()) { elytra().endFlight(); phase = Phase.FINAL_APPROACH; legStarted = false; return; }
        double x = CACHE.getPlayerCache().getX(), z = CACHE.getPlayerCache().getZ();
        if (Math.hypot(objX - x, objZ - z) <= cfg.netherExitRadius) {
            elytra().endFlight();
            onLegArrived();
            return;
        }
        if (!legStarted) {
            cfg.highway = true;
            cfg.ebounce = true;
            cfg.hasTarget = false;
            cfg.roadAnchorX = (int) Math.round(lineAx);     // push the road line to the pilot's e-bounce
            cfg.roadAnchorZ = (int) Math.round(lineAz);
            cfg.roadDirX = lineDx;
            cfg.roadDirZ = lineDz;
            if (chosenDir != null) cfg.highwayDir = chosenDir;   // display/fallback for spawn highways
            info("E-bounce along the road toward {}, {}.", (long) objX, (long) objZ);
            elytra().beginFlight();
            legStarted = true;
        }
        double perp = perpDist(x, z);
        if (perp > cfg.highwayAcquireRadius) {              // knocked far off the road — re-acquire instead of fighting
            elytra().endFlight();
            warn("Drifted {}b off the road — re-acquiring the centerline.", (long) perp);
            phase = Phase.NETHER_HW_ACQUIRE; legStarted = false; guardTicks = 0;
            return;
        }
        if ((guardTicks += THROTTLE_TICKS) > HIGHWAY_GUARD_TICKS)
            abort("nether road leg timed out before reaching the target");
    }

    /** Open-nether cruise straight to the objective's nether coords (highways off, or a route FLY leg). */
    private void tickNetherOpen(com.aquarius.util.config.Config.Client.Extra.ElytraPilot cfg) {
        if (!inNether()) { elytra().endFlight(); phase = Phase.FINAL_APPROACH; legStarted = false; return; }
        double x = CACHE.getPlayerCache().getX(), z = CACHE.getPlayerCache().getZ();
        if (Math.hypot(objX - x, objZ - z) <= cfg.netherExitRadius) {
            elytra().endFlight();
            onLegArrived();
            return;
        }
        if (!legStarted) {
            // Post-portal grace: the loaded bubble right after a dimension switch is tiny — give the first chunks
            // a moment to stream in before taking off (the spiral-hold covers the rest once airborne).
            if ((graceTicks += THROTTLE_TICKS) < cfg.tripNetherEntryGraceTicks) return;
            cfg.highway = false;
            cfg.ebounce = false;
            cfg.hasTarget = true;
            cfg.targetX = (int) Math.round(objX);
            cfg.targetZ = (int) Math.round(objZ);
            info("Open-nether cruise to {}, {} (roof-level, obstacle/lava-aware).", (long) objX, (long) objZ);
            elytra().beginFlight();
            legStarted = true;
        }
        if (elytra().isFlightDone()) {
            if (!elytra().wasFlightSuccessful()) { abort("the nether flight leg failed"); return; }
            elytra().endFlight();
            onLegArrived();      // landed early, before the exit radius — that's fine
            return;
        }
        if ((guardTicks += THROTTLE_TICKS) > HIGHWAY_GUARD_TICKS)
            abort("nether travel leg timed out before reaching the target");
    }

    /** A nether leg reached its objective: advance the route, or (coord trip) seek the exit portal. */
    private void onLegArrived() {
        if (activeRoute != null) { advanceLeg(); return; }
        info("Near the nether target ({}, {}) — looking for an exit portal.", (long) objX, (long) objZ);
        phase = Phase.NETHER_SEEK_EXIT; legStarted = false; guardTicks = 0;
    }

    /** Configure + start route leg {@code i}: its road line (ride) or open-nether target (fly), and the objective. */
    private void beginLeg(int i) {
        var cfg = CONFIG.client.extra.elytraPilot;
        legIndex = i;
        Route.Leg leg = activeRoute.legs().get(i);
        int prevX = (i == 0) ? 0 : activeRoute.legs().get(i - 1).x();
        int prevZ = (i == 0) ? 0 : activeRoute.legs().get(i - 1).z();
        objX = leg.x();
        objZ = leg.z();
        chosenDir = null;
        legStarted = false; guardTicks = 0; graceTicks = 0;
        if (leg.ride()) {
            setLine(prevX, prevZ, leg.x() - prevX, leg.z() - prevZ);   // road runs prev -> this endpoint
            cfg.roadY = leg.roadY();
            info("Route leg {}/{}: RIDE to {}, {} (road y{}).", i + 1, activeRoute.legs().size(), leg.x(), leg.z(), leg.roadY());
            phase = Phase.NETHER_HW_FLYIN;
        } else {
            info("Route leg {}/{}: FLY to {}, {}.", i + 1, activeRoute.legs().size(), leg.x(), leg.z());
            phase = Phase.NETHER_OPEN;
        }
    }

    /** Current route leg done — start the next, or finish/seek-exit at the end of the route. */
    private void advanceLeg() {
        int n = activeRoute.legs().size();
        if (legIndex + 1 < n) {
            info("Reached leg {} — moving to the next.", legIndex + 1);
            beginLeg(legIndex + 1);
            return;
        }
        if (activeRoute.endInNether()) {
            finish("arrived at the nether destination (route '" + activeRoute.id() + "')");
            return;
        }
        info("Reached the final route waypoint — looking for an exit portal.");
        phase = Phase.NETHER_SEEK_EXIT; legStarted = false; guardTicks = 0;
    }

    /** The destination IS in the nether: fly to the exact coords (full descend + land there), then we're done. */
    private void tickNetherDest(com.aquarius.util.config.Config.Client.Extra.ElytraPilot cfg) {
        if (!inNether()) { abort("left the nether unexpectedly"); return; }
        if (!legStarted) {
            if ((graceTicks += THROTTLE_TICKS) < cfg.tripNetherEntryGraceTicks) return;
            cfg.highway = false;
            cfg.ebounce = false;
            cfg.hasTarget = true;
            cfg.targetX = cfg.tripTargetX;   // nether coords, as entered — no 8:1 scaling
            cfg.targetZ = cfg.tripTargetZ;
            info("Flying to the nether destination {}, {}.", cfg.tripTargetX, cfg.tripTargetZ);
            elytra().beginFlight();
            legStarted = true;
            return;
        }
        if (elytra().isFlightDone()) {
            if (elytra().wasFlightSuccessful()) finish("arrived at the nether destination");
            else abort("the nether flight leg failed");
            return;
        }
        if ((guardTicks += THROTTLE_TICKS) > HIGHWAY_GUARD_TICKS)
            abort("nether travel leg timed out before reaching the destination");
    }

    private void tickNetherSeekExit() {
        if (inOverworld()) {
            info("Back in the overworld — final approach.");
            phase = Phase.FINAL_APPROACH;
            legStarted = false;
            guardTicks = 0;
            return;
        }
        elytra().endFlight();
        if (!BARITONE.isActive()) BARITONE.getTo(BlockRegistry.NETHER_PORTAL);
        if ((guardTicks += THROTTLE_TICKS) > PORTAL_GUARD_TICKS)
            abort("no exit portal near the target's nether coords — build a portal there and retry");
    }

    private void tickFinalApproach(int tx, int tz) {
        if (!legStarted) { startFlightTo(tx, tz); return; }
        if (elytra().isFlightDone()) {
            if (elytra().wasFlightSuccessful()) finish("arrived at the destination");
            else abort("the final flight leg failed");   // e.g. takeoff failed — never report a failed leg as arrival
        }
    }

    // --- helpers ---

    private ElytraPilot elytra() {
        return MODULE.get(ElytraPilot.class);
    }

    private boolean inNether() {
        return World.getCurrentDimension() == DimensionRegistry.THE_NETHER.get();
    }

    private boolean inOverworld() {
        return World.getCurrentDimension() == DimensionRegistry.OVERWORLD.get();
    }

    private void startFlightTo(int tx, int tz) {
        var cfg = CONFIG.client.extra.elytraPilot;
        cfg.hasTarget = true;
        cfg.highway = false;
        cfg.ebounce = false;
        cfg.targetX = tx;
        cfg.targetZ = tz;
        elytra().beginFlight();
        legStarted = true;
    }

    /**
     * Pick the highway to ride toward the target's nether coords: among the directions that head TOWARD the target
     * (outbound · target-bearing &gt; 0), choose the one whose LINE the DESTINATION lies nearest to (smallest
     * perpendicular distance from the target to the line). This guarantees the chosen road actually passes as close
     * as a highway can to the destination — the bot rides it, then branches off (a later leg) for the rest.
     * Degenerate case (bearing perpendicular to every line) falls back to the best bearing match.
     */
    private HighwayDir chooseHighway(double nx, double nz) {
        double len = Math.hypot(nx, nz);
        if (len < 1e-6) return HighwayDir.N;
        double ux = nx / len, uz = nz / len;              // bearing from (0,0) to the target's nether coords
        HighwayDir best = null;
        double bestPerp = Double.MAX_VALUE, bestDot = -2, fbDot = -2;
        HighwayDir fallback = HighwayDir.N;
        for (HighwayDir d : HighwayDir.values()) {
            double[] u = unit(d);
            double dot = ux * u[0] + uz * u[1];
            if (dot > fbDot) { fbDot = dot; fallback = d; }
            if (dot <= 0) continue;                       // doesn't head toward the target — not a candidate
            double perp = perpDistToLine(nx, nz, d);      // how far the DESTINATION sits off this road
            if (perp < bestPerp || (perp == bestPerp && dot > bestDot)) { bestPerp = perp; bestDot = dot; best = d; }
        }
        return best != null ? best : fallback;
    }

    /** Perpendicular distance from (cx,cz) to the infinite line (through 0,0) that carries highway {@code dir}. */
    private double perpDistToLine(double cx, double cz, HighwayDir dir) {
        return switch (dir) {
            case N, S   -> Math.abs(cx);                  // X=0  line (N/S)
            case E, W   -> Math.abs(cz);                  // Z=0  line (E/W)
            case NE, SW -> Math.abs(cx + cz) * HW_S;      // x+z=0 line (NE/SW)
            case SE, NW -> Math.abs(cx - cz) * HW_S;      // x−z=0 line (SE/NW)
        };
    }

    /** Unit (dx,dz) of each highway. MC: +X=east, +Z=south. */
    private double[] unit(HighwayDir d) {
        return switch (d) {
            case N  -> new double[]{ 0, -1 };
            case S  -> new double[]{ 0,  1 };
            case E  -> new double[]{ 1,  0 };
            case W  -> new double[]{-1,  0 };
            case NE -> new double[]{ HW_S, -HW_S };
            case SE -> new double[]{ HW_S,  HW_S };
            case SW -> new double[]{-HW_S,  HW_S };
            case NW -> new double[]{-HW_S, -HW_S };
        };
    }

    // --- current road line (anchor + unit dir): generalises acquisition to ANY straight road, not just origin ones ---

    /** Set the current e-bounce road line: anchor (ax,az) + a direction that gets normalised to a unit vector. */
    private void setLine(double ax, double az, double dx, double dz) {
        double len = Math.hypot(dx, dz);
        if (len < 1e-9) { dx = 1; dz = 0; len = 1; }   // degenerate (zero-length leg) — arbitrary axis
        lineAx = ax; lineAz = az; lineDx = dx / len; lineDz = dz / len;
    }

    /** Perpendicular distance from (px,pz) to the current road line (anchor + unit dir): |(P−A) × dir|. */
    private double perpDist(double px, double pz) {
        return Math.abs((px - lineAx) * lineDz - (pz - lineAz) * lineDx);
    }

    /** Signed distance along the current road line from its anchor to the foot of (px,pz): (P−A) · dir. */
    private double footT(double px, double pz) {
        return (px - lineAx) * lineDx + (pz - lineAz) * lineDz;
    }

    /** Clear the custom road line so a later plain trip / {@code /fly highway} rides a clean spawn highway. */
    private void resetCustomLine() {
        var cfg = CONFIG.client.extra.elytraPilot;
        cfg.roadAnchorX = 0; cfg.roadAnchorZ = 0; cfg.roadDirX = 0; cfg.roadDirZ = 0;
    }

    /** Tear down route state on end/cancel so the next trip starts clean (no stale route or custom road line). */
    private void clearRouteState() {
        activeRoute = null;
        legIndex = 0;
        resetCustomLine();
        CONFIG.client.extra.elytraPilot.tripActiveRoute = "";
    }

    private void finish(String why) {
        phase = Phase.DONE;
        elytra().endFlight();
        BARITONE.stop();
        CONFIG.client.extra.elytraPilot.tripActive = false;
        clearRouteState();
        inGameAlertActivePlayer("<green>Trip complete: " + why);
        info("Trip complete: {}", why);
        // Actually disable the module so the NEXT `fly trip` produces a fresh enable edge — without this the
        // module stays enabled in DONE/FAILED and re-arming via the command is a silent no-op (onEnable never runs).
        syncEnabledFromConfig();
    }

    private void abort(String why) {
        phase = Phase.FAILED;
        elytra().endFlight();
        BARITONE.stop();
        CONFIG.client.extra.elytraPilot.tripActive = false;
        clearRouteState();
        inGameAlertActivePlayer("<red>Trip aborted: " + why);
        warn("Trip aborted: {}", why);
        syncEnabledFromConfig(); // same as finish(): leave the module truly disabled so a re-fire re-enables it
    }
}
