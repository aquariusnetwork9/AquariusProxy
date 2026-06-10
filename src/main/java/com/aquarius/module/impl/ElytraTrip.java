package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.event.client.ClientBotTick;
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
 * <p>Routing decision, from the destination's distance to spawn (0,0):
 * <ul>
 *   <li><b>Within {@code spawnRegionRadius}</b> (default 100k): fly the overworld straight to the target.</li>
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
        OW_DIRECT,         // destination within the spawn region: fly the overworld straight there
        ENTER_NETHER,      // walk into the nearest portal, wait for the nether
        NETHER_HIGHWAY,    // e-bounce along the nearest highway toward the target's nether coords
        NETHER_SEEK_EXIT,  // near the target's nether coords: walk into an exit portal, wait for the overworld
        FINAL_APPROACH,    // fly the overworld to the exact target
        DONE,
        FAILED
    }

    private static final double NETHER_SCALE = 8.0;
    private static final int THROTTLE_TICKS = 20;        // run the planner ~once a second
    private static final int PORTAL_GUARD_TICKS = 3600;  // ~3 min on a portal leg before giving up
    private static final int HIGHWAY_GUARD_TICKS = 24000;// ~20 min on a single highway leg before giving up
    private static final double HW_S = 0.7071067811865476; // 1/sqrt(2)

    private Phase phase = Phase.IDLE;
    private int tickCtr;
    private int guardTicks;
    private boolean legStarted;

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.elytraPilot.tripActive;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(ClientBotTick.Stopped.class, e -> { phase = Phase.IDLE; legStarted = false; })
        );
    }

    @Override
    public void onEnable() {
        var cfg = CONFIG.client.extra.elytraPilot;
        guardTicks = 0;
        legStarted = false;
        double dist = Math.hypot(cfg.tripTargetX, cfg.tripTargetZ);
        if (dist <= cfg.spawnRegionRadius) {
            phase = Phase.OW_DIRECT;
            info("Trip: {}, {} is within the spawn region ({}b) — flying overworld-direct.",
                cfg.tripTargetX, cfg.tripTargetZ, cfg.spawnRegionRadius);
        } else {
            phase = Phase.ENTER_NETHER;
            info("Trip: {}, {} is {}b out — routing via the nether (nether target {}, {}).",
                cfg.tripTargetX, cfg.tripTargetZ, (long) dist,
                Math.round(cfg.tripTargetX / NETHER_SCALE), Math.round(cfg.tripTargetZ / NETHER_SCALE));
        }
    }

    @Override
    public void onDisable() {
        phase = Phase.IDLE;
        legStarted = false;
        elytra().endFlight();
        BARITONE.stop();
    }

    private void onTick(final ClientBotTick event) {
        if (phase == Phase.IDLE || phase == Phase.DONE || phase == Phase.FAILED) return;
        if (++tickCtr % THROTTLE_TICKS != 0) return; // ~1 Hz planner; the active leg runs every tick on its own
        try {
            var cfg = CONFIG.client.extra.elytraPilot;
            switch (phase) {
                case OW_DIRECT        -> tickOwDirect(cfg.tripTargetX, cfg.tripTargetZ);
                case ENTER_NETHER     -> tickEnterNether();
                case NETHER_HIGHWAY   -> tickNetherHighway(cfg);
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

    private void tickOwDirect(int tx, int tz) {
        if (!legStarted) { startFlightTo(tx, tz); return; }
        if (elytra().isFlightDone()) finish("arrived (overworld-direct)");
    }

    private void tickEnterNether() {
        if (inNether()) {
            info("In the nether — heading for a highway.");
            phase = Phase.NETHER_HIGHWAY;
            legStarted = false;
            guardTicks = 0;
            return;
        }
        elytra().endFlight();                                           // don't let flight fight Baritone
        if (!BARITONE.isActive()) BARITONE.getTo(BlockRegistry.NETHER_PORTAL); // walk into the nearest portal
        if ((guardTicks += THROTTLE_TICKS) > PORTAL_GUARD_TICKS)
            abort("no reachable nether portal — move the bot near a portal (or build one) and retry");
    }

    private void tickNetherHighway(com.aquarius.util.config.Config.Client.Extra.ElytraPilot cfg) {
        if (!inNether()) { // fell back to the overworld unexpectedly (stray portal) — re-evaluate from here
            elytra().endFlight();
            phase = Phase.FINAL_APPROACH;
            legStarted = false;
            return;
        }
        double nx = cfg.tripTargetX / NETHER_SCALE, nz = cfg.tripTargetZ / NETHER_SCALE;
        double x = CACHE.getPlayerCache().getX(), z = CACHE.getPlayerCache().getZ();
        if (Math.hypot(nx - x, nz - z) <= cfg.netherExitRadius) {
            elytra().endFlight();
            info("Near the nether target ({}, {}) — looking for an exit portal.", (long) nx, (long) nz);
            phase = Phase.NETHER_SEEK_EXIT;
            legStarted = false;
            guardTicks = 0;
            return;
        }
        if (!legStarted) {
            if (cfg.tripUseHighways) {
                HighwayDir dir = nearestHighway(nx, nz);
                cfg.highway = true;
                cfg.highwayDir = dir;
                cfg.ebounce = true;
                cfg.roadY = 120;
                cfg.hasTarget = false;
                info("Nether highway: bouncing {} toward the target.", dir);
            } else {
                // open-nether: cruise straight to the target's nether coords (roof-level, obstacle + lava aware)
                cfg.highway = false;
                cfg.ebounce = false;
                cfg.hasTarget = true;
                cfg.targetX = (int) Math.round(nx);
                cfg.targetZ = (int) Math.round(nz);
                info("Open-nether cruise to {}, {} (roof-level, obstacle/lava-aware).", (long) nx, (long) nz);
            }
            elytra().beginFlight();
            legStarted = true;
        }
        // open-nether cruise may land before we reach the exit radius — that's fine, seek a portal from there
        if (!cfg.tripUseHighways && elytra().isFlightDone()) {
            elytra().endFlight();
            phase = Phase.NETHER_SEEK_EXIT;
            legStarted = false;
            guardTicks = 0;
            return;
        }
        if ((guardTicks += THROTTLE_TICKS) > HIGHWAY_GUARD_TICKS)
            abort("nether travel leg timed out before reaching the target");
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
        if (elytra().isFlightDone()) finish("arrived at the destination");
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

    /** The highway whose outward direction from (0,0) best matches the bearing to (dx,dz). */
    private HighwayDir nearestHighway(double dx, double dz) {
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) return HighwayDir.N;
        double ux = dx / len, uz = dz / len;
        HighwayDir best = HighwayDir.N;
        double bestDot = -2;
        for (HighwayDir d : HighwayDir.values()) {
            double[] u = unit(d);
            double dot = ux * u[0] + uz * u[1];
            if (dot > bestDot) { bestDot = dot; best = d; }
        }
        return best;
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

    private void finish(String why) {
        phase = Phase.DONE;
        elytra().endFlight();
        BARITONE.stop();
        CONFIG.client.extra.elytraPilot.tripActive = false; // leg inert; module goes idle (re-sync disables it)
        inGameAlertActivePlayer("<green>Trip complete: " + why);
        info("Trip complete: {}", why);
    }

    private void abort(String why) {
        phase = Phase.FAILED;
        elytra().endFlight();
        BARITONE.stop();
        CONFIG.client.extra.elytraPilot.tripActive = false;
        inGameAlertActivePlayer("<red>Trip aborted: " + why);
        warn("Trip aborted: {}", why);
    }
}
