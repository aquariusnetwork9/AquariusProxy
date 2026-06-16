package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.cache.data.entity.Entity;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.player.World;
import com.aquarius.module.api.Module;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.InteractAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;

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
    private static final double TURN_DEADBAND = 5.0;    // |yaw error| below this: stop steering, go straight
    private static final double THRUST_CONE = 70.0;     // only thrust forward when within this |yaw error| of the target
    private static final int STUCK_TICKS = 120;         // GOTO: ticks of no XZ progress before giving up
    private static final double STUCK_EPS_SQ = 0.5 * 0.5;

    private Mode mode = Mode.IDLE;
    private boolean mForward, mBack, mLeft, mRight; // MANUAL held input
    private double targetX, targetZ;                 // GOTO target
    private double lastX, lastZ;
    private int noProgressTicks;

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
        double distSq = dx * dx + dz * dz;
        if (distSq <= ARRIVE_RADIUS * ARRIVE_RADIUS) {
            info("Arrived at " + (int) targetX + ", " + (int) targetZ);
            mode = Mode.IDLE;
            BOT.submitBoatInput(false, false, false, false);
            return;
        }

        // stuck detection: no XZ progress for a while (beached / walled in)
        double moveSq = (x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ);
        if (moveSq < STUCK_EPS_SQ) {
            if (++noProgressTicks >= STUCK_TICKS) {
                warn("No progress toward " + (int) targetX + ", " + (int) targetZ + " for "
                    + STUCK_TICKS + " ticks — stopping (beached or blocked).");
                mode = Mode.IDLE;
                BOT.submitBoatInput(false, false, false, false);
                return;
            }
        } else {
            noProgressTicks = 0;
        }
        lastX = x;
        lastZ = z;

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
        BOT.submitBoatInput(false, false, false, false);
    }

    public void goTo(double x, double z) {
        mode = Mode.GOTO;
        targetX = x;
        targetZ = z;
        lastX = Double.NaN;
        lastZ = Double.NaN;
        noProgressTicks = 0;
    }

    public Mode getMode() {
        return mode;
    }
}
