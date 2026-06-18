package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.cache.data.entity.Entity;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.player.World;
import com.aquarius.module.api.Module;
import com.aquarius.util.math.MathHelper;
import it.unimi.dsi.fastutil.longs.LongList;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.InteractAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;

import java.util.ArrayDeque;
import java.util.List;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.BOT;
import static com.aquarius.Globals.CACHE;

/**
 * Boat — guidance layer for self-driven boats. The boat physics itself lives in {@link com.aquarius.feature.player.Bot}
 * (a server-side port of Minecraft's {@code AbstractBoat} tick); this module decides the per-tick steering input and
 * hands it to {@code Bot.submitBoatInput(...)} each tick, exactly like {@link ElytraPilot} drives the e-bounce synth.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>MANUAL</b> — hold a fixed input (forward / turn) set via {@code .boat fwd|back|left|right}. Used to
 *       validate the physics against the live server (Phase 1).</li>
 *   <li><b>GOTO</b> — steer toward an (x,z) target: turn to face it, thrust while roughly aligned, stop on arrival.
 *       First cut — depends on the MANUAL validation proving the sim tracks the server.</li>
 * </ul>
 *
 * <p>Prerequisite: the bot must already be seated in a boat (e.g. a controlling player right-clicks one). This module
 * does not mount; it only steers a boat the bot is already in.
 */
public class Boat extends Module {

    public enum Mode { IDLE, MANUAL, GOTO }

    private static final double MOUNT_RANGE_SQ = 8.0 * 8.0; // mount the nearest empty boat within this distance
    private static final double ARRIVE_RADIUS = 3.0;   // within this many blocks of the target counts as arrived
    private static final double WAYPOINT_RADIUS = 2.5; // within this many blocks of a waypoint: advance to the next
    private static final double TURN_DEADBAND = 5.0;    // |yaw error| below this: stop steering, go straight
    private static final double THRUST_CONE = 70.0;     // only thrust forward when within this |yaw error| of the target
    private static final int STUCK_TICKS = 200;         // GOTO: ticks of no XZ progress before giving up
    private static final int STUCK_REPLAN_TICKS = 50;   // ticks of no progress that force an early re-plan first
    private static final double STUCK_EPS_SQ = 0.5 * 0.5;
    private static final int REPLAN_TICKS = 40;         // re-run A* at least this often (new chunks / moving target)

    private Mode mode = Mode.IDLE;
    private boolean mForward, mBack, mLeft, mRight; // MANUAL held input
    private double targetX, targetZ;                 // GOTO final target
    private double lastX, lastZ;
    private int noProgressTicks;

    // --- obstacle avoidance (A* over the water surface; see BoatPathfinder) ---
    private boolean avoid = true;                    // route around land vs. steer straight at the target
    private int planRadius = 64;                     // A* search window half-size, in blocks
    private final ArrayDeque<double[]> waypoints = new ArrayDeque<>(); // queued {x,z} cell centres to follow
    private int replanCooldown;                      // ticks until the next scheduled re-plan

    @Override
    public boolean enabledSetting() {
        return false; // command-driven; not auto-enabled on startup
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::onTick),
            of(ClientBotTick.Stopped.class, e -> mode = Mode.IDLE)
        );
    }

    @Override
    public void onEnable() {
        BOT.startBoatControl();
    }

    @Override
    public void onDisable() {
        BOT.stopBoatControl();
        mode = Mode.IDLE;
    }

    private void onTick(final ClientBotTick event) {
        var player = CACHE.getPlayerCache().getThePlayer();
        if (player == null || !player.isInVehicle()) {
            BOT.submitBoatInput(false, false, false, false);
            return;
        }
        switch (mode) {
            case MANUAL -> BOT.submitBoatInput(mForward, mBack, mLeft, mRight);
            case GOTO -> tickGoto(player.getVehicleId());
            default -> BOT.submitBoatInput(false, false, false, false);
        }
    }

    private void tickGoto(final int vehicleId) {
        var boat = CACHE.getEntityCache().get(vehicleId);
        if (boat == null) {
            BOT.submitBoatInput(false, false, false, false);
            return;
        }
        double x = boat.getX();
        double z = boat.getZ();
        double dx = targetX - x;
        double dz = targetZ - z;
        if (dx * dx + dz * dz <= ARRIVE_RADIUS * ARRIVE_RADIUS) {
            info("Arrived at " + (int) targetX + ", " + (int) targetZ);
            stopSteering();
            return;
        }

        // Stuck detection: no XZ progress (beached / walled in). Force an early re-plan before giving up entirely.
        double moveSq = (x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ);
        if (moveSq < STUCK_EPS_SQ) {
            noProgressTicks++;
            if (avoid && noProgressTicks == STUCK_REPLAN_TICKS) {
                replanCooldown = 0; // try a fresh route around whatever we're stuck on
            }
            if (noProgressTicks >= STUCK_TICKS) {
                warn("No progress toward " + (int) targetX + ", " + (int) targetZ + " for "
                    + STUCK_TICKS + " ticks — stopping (beached or no water route).");
                stopSteering();
                return;
            }
        } else {
            noProgressTicks = 0;
        }
        lastX = x;
        lastZ = z;

        if (!avoid) {                       // avoidance off: original behaviour — point straight at the target
            steerToward(boat, targetX, targetZ);
            return;
        }

        int surfaceY = MathHelper.floorI(boat.getY());
        // Re-plan on a cadence, or immediately when the next waypoint just became land (a chunk revealed it).
        // (A failed plan leaves the queue empty; we deliberately wait out the cooldown instead of re-running A*
        // every tick. The stuck-timer above zeroes the cooldown to retry sooner when we're genuinely blocked.)
        boolean nextStale = !waypoints.isEmpty() && isWaypointBlocked(waypoints.peek(), surfaceY);
        if (--replanCooldown <= 0 || nextStale) {
            replan(x, z, surfaceY);
        }
        // Drop waypoints we've reached.
        double[] wp;
        while ((wp = waypoints.peek()) != null
            && (x - wp[0]) * (x - wp[0]) + (z - wp[1]) * (z - wp[1]) <= WAYPOINT_RADIUS * WAYPOINT_RADIUS) {
            waypoints.poll();
        }
        wp = waypoints.peek();
        if (wp == null) {
            steerToward(boat, targetX, targetZ); // no route found — head straight and rely on the stuck timer
        } else {
            steerToward(boat, wp[0], wp[1]);
        }
    }

    /** Run A* from the boat to the target and load the resulting waypoints; empties the queue if no route exists. */
    private void replan(double x, double z, int surfaceY) {
        replanCooldown = REPLAN_TICKS;
        waypoints.clear();
        LongList cells = BoatPathfinder.plan(x, z, surfaceY, targetX, targetZ, planRadius);
        if (cells == null) return;
        for (int i = 0; i < cells.size(); i++) {
            long c = cells.getLong(i);
            waypoints.add(new double[] { (int) (c >> 32) + 0.5, (int) c + 0.5 });
        }
    }

    private static boolean isWaypointBlocked(double[] wp, int surfaceY) {
        return !BoatPathfinder.isNavigable(MathHelper.floorI(wp[0]), surfaceY, MathHelper.floorI(wp[1]));
    }

    /** Turn toward (tx,tz) and thrust while roughly aligned — the shared low-level steering primitive. */
    private void steerToward(final Entity boat, double tx, double tz) {
        double dx = tx - boat.getX();
        double dz = tz - boat.getZ();
        // MC yaw: 0 faces +Z, +yaw turns toward -X. Heading to face (dx,dz) is atan2(-dx, dz).
        double desiredYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double err = wrapDegrees(desiredYaw - boat.getYaw());
        boolean right = err > TURN_DEADBAND;   // increase yaw
        boolean left = err < -TURN_DEADBAND;   // decrease yaw
        boolean forward = Math.abs(err) < THRUST_CONE;
        BOT.submitBoatInput(forward, false, left, right);
    }

    private static double wrapDegrees(double deg) {
        deg %= 360.0;
        if (deg >= 180.0) deg -= 360.0;
        if (deg < -180.0) deg += 360.0;
        return deg;
    }

    // ----- command surface -----

    /** Right-click the nearest empty boat to seat the bot, and arm boat control. */
    public boolean mountNearestBoat() {
        var pc = CACHE.getPlayerCache().getThePlayer();
        if (pc == null) {
            warn("No player to mount with.");
            return false;
        }
        if (pc.isInVehicle()) {
            info("Already seated in a vehicle.");
            return false;
        }
        double px = pc.getX();
        double py = pc.getY();
        double pz = pc.getZ();
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (var e : CACHE.getEntityCache().getEntities().values()) {
            if (e.isRemoved() || !World.isBoat(e.getEntityType())) continue;
            if (!e.getPassengerIds().isEmpty()) continue; // already occupied
            double dx = e.getX() - px, dy = e.getY() - py, dz = e.getZ() - pz;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        if (nearest == null || best > MOUNT_RANGE_SQ) {
            warn("No empty boat within " + (int) Math.sqrt(MOUNT_RANGE_SQ) + " blocks to mount.");
            return false;
        }
        // Vanilla right-click on an entity sends INTERACT_AT then INTERACT; the latter seats you in a boat.
        sendClientPacketAsync(new ServerboundInteractPacket(
            nearest.getEntityId(), InteractAction.INTERACT_AT, 0f, 0f, 0f, Hand.MAIN_HAND, false));
        sendClientPacketAsync(new ServerboundInteractPacket(
            nearest.getEntityId(), InteractAction.INTERACT, Hand.MAIN_HAND, false));
        enable();
        info("Mounting boat " + nearest.getEntityId() + " (~" + (int) Math.sqrt(best) + " blocks away).");
        return true;
    }

    public void setManual(boolean forward, boolean back, boolean left, boolean right) {
        mode = Mode.MANUAL;
        mForward = forward;
        mBack = back;
        mLeft = left;
        mRight = right;
    }

    public void stopSteering() {
        mode = Mode.IDLE;
        mForward = mBack = mLeft = mRight = false;
        waypoints.clear();
        noProgressTicks = 0;
        BOT.submitBoatInput(false, false, false, false);
    }

    public void goTo(double x, double z) {
        mode = Mode.GOTO;
        targetX = x;
        targetZ = z;
        lastX = Double.NaN;
        lastZ = Double.NaN;
        noProgressTicks = 0;
        waypoints.clear();
        replanCooldown = 0; // plan immediately on the next tick
    }

    /** Toggle routing around land. Off = steer straight at the target (original behaviour). */
    public void setAvoid(boolean avoid) {
        this.avoid = avoid;
        replanCooldown = 0;
        waypoints.clear();
    }

    public boolean isAvoid() {
        return avoid;
    }

    /** A* search window half-size, in blocks. Larger = routes around bigger landmasses, more CPU per plan. */
    public void setPlanRadius(int radius) {
        this.planRadius = Math.max(8, Math.min(256, radius));
        replanCooldown = 0;
    }

    public int getPlanRadius() {
        return planRadius;
    }

    public Mode getMode() {
        return mode;
    }
}
