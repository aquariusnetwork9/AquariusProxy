package com.aquarius.feature.combat;

import org.jspecify.annotations.Nullable;

/**
 * Arrow ballistics solver for {@link com.aquarius.module.impl.AutoBow}. Vanilla arrows are launched along the look
 * vector at a fixed speed and then fall under gravity with air drag, so hitting anything past point-blank needs the
 * pitch raised to compensate for the drop. There is no clean closed form once drag is involved, so we forward-simulate
 * the trajectory (the exact vanilla integration order: {@code pos += vel; vel *= drag; vel.y -= gravity}) for candidate
 * pitches and pick the flattest one whose arc passes through the target.
 *
 * <p>All inputs/outputs are server coordinates and Minecraft yaw/pitch degrees (pitch negative = aiming up).
 */
public final class BowBallistics {
    private BowBallistics() {}

    /** Per-tick velocity multiplier (air). */
    public static final double DRAG = 0.99;
    /** Per-tick downward acceleration applied after drag. */
    public static final double GRAVITY = 0.05;
    /** Arrow launch speed at a full bow draw (blocks/tick). */
    public static final double BOW_SPEED = 3.0;
    /** Crossbow bolt launch speed (blocks/tick) — slightly faster than a bow. */
    public static final double CROSSBOW_SPEED = 3.15;

    /** A firing solution: the yaw/pitch to look at and the estimated time of flight in ticks. */
    public record Solution(float yaw, float pitch, int flightTicks) {}

    /**
     * Solve for the look angles that land an arrow of launch speed {@code v0} (from the launch point) on the target
     * point. Prefers the flatter (faster, harder-to-dodge) of the two possible arcs. Returns {@code null} if the target
     * is out of reach for that speed.
     *
     * @param lx,ly,lz launch position (the bot's eye)
     * @param tx,ty,tz target aim point
     * @param v0       arrow launch speed (use {@link #BOW_SPEED} / {@link #CROSSBOW_SPEED})
     * @param maxTicks trajectory simulation cap (also the longest flight we'll accept)
     */
    public static @Nullable Solution solve(double lx, double ly, double lz,
                                           double tx, double ty, double tz,
                                           double v0, int maxTicks) {
        double dx = tx - lx, dz = tz - lz;
        double distH = Math.sqrt(dx * dx + dz * dz);
        double dy = ty - ly;
        float yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));

        // Scan pitch from flat-up toward steep-up, taking the first arc that reaches the target height at distH.
        // Two solutions can exist (direct + lobbed); scanning low-to-high magnitude yields the flat/direct one first.
        Solution best = null;
        double bestErr = Double.MAX_VALUE;
        double prevErr = Double.NaN;
        for (double pitch = 30.0; pitch >= -89.0; pitch -= 0.5) {
            double[] r = heightAtDistance(v0, Math.toRadians(pitch), distH, maxTicks);
            if (r == null) continue;                 // never reaches distH (fell short)
            double err = r[0] - dy;                   // predicted height minus required height
            if (!Double.isNaN(prevErr) && (prevErr <= 0) != (err <= 0)) {
                // sign change between this step and the last → root is bracketed; refine and return (flattest arc)
                double refinedPitch = refine(v0, distH, dy, pitch + 0.5, pitch, maxTicks);
                double[] rr = heightAtDistance(v0, Math.toRadians(refinedPitch), distH, maxTicks);
                int ticks = rr != null ? (int) rr[1] : maxTicks;
                return new Solution(yaw, (float) refinedPitch, ticks);
            }
            prevErr = err;
            double aerr = Math.abs(err);
            if (aerr < bestErr) { bestErr = aerr; best = new Solution(yaw, (float) pitch, (int) r[1]); }
        }
        // No exact bracket (e.g. extreme range) — return the closest-miss aim if it's within ~1.5 blocks, else null.
        return bestErr <= 1.5 ? best : null;
    }

    /** Bisection refinement of the pitch root between two bracketing degrees. */
    private static double refine(double v0, double distH, double dy, double hi, double lo, int maxTicks) {
        for (int i = 0; i < 18; i++) {
            double mid = (hi + lo) / 2.0;
            double[] r = heightAtDistance(v0, Math.toRadians(mid), distH, maxTicks);
            double err = (r == null ? -1e9 : r[0] - dy);
            // err decreases as pitch increases (aim lower) for the direct arc; keep the bracket around the root
            double[] rHi = heightAtDistance(v0, Math.toRadians(hi), distH, maxTicks);
            double errHi = (rHi == null ? -1e9 : rHi[0] - dy);
            if ((errHi <= 0) != (err <= 0)) lo = mid; else hi = mid;
        }
        return (hi + lo) / 2.0;
    }

    /**
     * Simulate an arrow launched at {@code pitchRad} and return {@code [heightAtDistH, flightTicks]} when its horizontal
     * travel first reaches {@code distH} (linearly interpolated within the crossing tick), or {@code null} if it never
     * gets there within {@code maxTicks}. Horizontal speed is taken along the target bearing, so only the vertical plane
     * is simulated.
     */
    private static @Nullable double[] heightAtDistance(double v0, double pitchRad, double distH, int maxTicks) {
        double cos = Math.cos(pitchRad);
        double vH = v0 * cos;                 // horizontal speed
        double vy = v0 * -Math.sin(pitchRad); // up is positive (pitch negative = up)
        double h = 0, y = 0;
        double prevH = 0, prevY = 0;
        for (int t = 0; t < maxTicks; t++) {
            prevH = h; prevY = y;
            h += vH; y += vy;                 // pos += vel
            vH *= DRAG; vy *= DRAG;           // vel *= drag
            vy -= GRAVITY;                    // gravity after drag
            if (h >= distH) {
                double frac = (distH - prevH) / (h - prevH);
                return new double[]{ prevY + (y - prevY) * frac, t + frac };
            }
        }
        return null;
    }
}
