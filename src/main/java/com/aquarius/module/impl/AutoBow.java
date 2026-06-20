package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.cache.data.entity.EntityLiving;
import com.aquarius.cache.data.entity.EntityPlayer;
import com.aquarius.cache.data.entity.EntityStandard;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.combat.BowBallistics;
import com.aquarius.feature.combat.BowBallistics.Solution;
import com.aquarius.feature.player.ClickTarget;
import com.aquarius.feature.player.Input;
import com.aquarius.feature.player.InputRequest;
import com.aquarius.feature.player.InputRequestFuture;
import com.aquarius.feature.player.raycast.RaycastHelper;
import com.aquarius.feature.pathfinder.movement.MovementHelper;
import com.aquarius.mc.block.Direction;
import com.aquarius.mc.item.ItemRegistry;
import com.aquarius.util.RequestFuture;
import com.aquarius.util.math.MathHelper;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.*;

/**
 * Ranged combat: draws and fires a bow (or charges and fires a crossbow) at hostiles in a configurable distance band.
 * It deliberately leaves point-blank targets to {@link KillAura} (melee) and only engages the gap from {@code minRange}
 * out to {@code maxRange}, so the two cooperate — KillAura runs mobs down up close, AutoBow shoots the ones still at
 * range (skeletons, kiting mobs). {@code WhisperControl}'s {@code protect} turns it on alongside KillAura.
 *
 * <h2>How a shot is taken</h2>
 * Each tick, with a target selected and a bow/crossbow swapped to hand, it solves the {@link BowBallistics ballistics}
 * (gravity-compensated pitch, optional target leading) and aims there. Then it runs the vanilla use sequence proven by
 * the packet capture: a single {@code rightClick} input starts the draw ({@code ServerboundUseItemPacket} with the
 * prediction sequence), it holds and keeps aiming for the draw/charge ticks (TPS-scaled), and fires with a
 * {@code RELEASE_USE_ITEM} action — exactly the shape AutoEat/AutoOmen and the capture confirm. A crossbow instead
 * <i>loads</i> on release and fires on the next {@code rightClick}.
 */
public class AutoBow extends AbstractInventoryModule {

    private int cooldown = 0;
    private boolean drawing = false;          // a bow draw / crossbow charge is in progress
    private int chargeTicks = 0;              // ticks held in the current draw/charge
    private boolean crossbowReady = false;    // a crossbow finished loading and is waiting to fire
    private final WeakReference<EntityLiving> nullRef = new WeakReference<>(null);
    private WeakReference<EntityLiving> target = nullRef;
    private RequestFuture swapFuture = RequestFuture.rejected;

    // per-target velocity estimate (for leading), refreshed from position deltas
    private int velEntityId = -1;
    private double lastTx, lastTy, lastTz, velX, velY, velZ;

    // arrow-dodge / kite state
    private final Map<Integer, double[]> arrowTrack = new HashMap<>();   // arrowId -> {x,y,z,velX,velY,velZ}
    private int kiteDir = 1;            // current kite strafe side (+1 right, -1 left), flipped periodically
    private int kiteFlipCtr = 0;        // ticks until the next kite direction flip
    private int currentStrafe = 0;      // strafe applied this tick: -1 left, +1 right, 0 none

    public AutoBow() {
        super(HandRestriction.MAIN_HAND, 0);
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.autoBow.enabled;
    }

    @Override
    public int getPriority() {
        // default ABOVE Baritone (7000) so AutoBow can drive aim+strafe together (mobile combat) while still below
        // KillAura (8000) so melee wins once a mob closes in. Input is winner-take-all, so movement must ride with aim.
        return Objects.requireNonNullElse(CONFIG.client.extra.autoBow.priority, 7500);
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::handleClientTick),
            of(ClientBotTick.Starting.class, e -> reset()),
            of(ClientBotTick.Stopped.class, e -> reset())
        );
    }

    @Override
    public void onEnable() { reset(); }

    @Override
    public void onDisable() { reset(); }

    private void reset() {
        cooldown = 0;
        drawing = false;
        chargeTicks = 0;
        crossbowReady = false;
        target = nullRef;
        swapFuture = RequestFuture.rejected;
        velEntityId = -1;
        arrowTrack.clear();
        currentStrafe = 0;
        kiteFlipCtr = 0;
    }

    private void handleClientTick(final ClientBotTick e) {
        var cfg = CONFIG.client.extra.autoBow;
        if (!CACHE.getPlayerCache().getThePlayer().isAlive()
            || CACHE.getPlayerCache().getGameMode() == GameMode.CREATIVE
            || CACHE.getPlayerCache().getGameMode() == GameMode.SPECTATOR) {
            if (drawing) cancelDraw();
            return;
        }
        updateArrowTracking();                 // keep arrow velocity estimates fresh every tick (for dodging)
        currentStrafe = 0;
        if (cooldown > 0) { cooldown--; return; }
        if (!swapFuture.isDone()) { INPUTS.submit(InputRequest.noInput(this, getPriority())); return; }

        EntityLiving tgt = findTarget(cfg);
        if (tgt == null) {                        // nothing to shoot — abandon any half-drawn shot
            if (drawing) cancelDraw();
            crossbowReady = false;
            target = nullRef;
            return;
        }
        if (!target.refersTo(tgt)) target = new WeakReference<>(tgt);

        // make sure a bow/crossbow is in hand
        var inv = doInventoryActionsV2();
        switch (inv.state()) {
            case NO_ITEM -> { if (drawing) cancelDraw(); return; }   // no usable weapon/arrows
            case SWAPPING -> { swapFuture = inv.inventoryActionFuture(); INPUTS.submit(InputRequest.noInput(this, getPriority())); return; }
            case ITEM_IN_HAND -> { /* proceed */ }
        }

        boolean crossbow = isHeldCrossbow();
        double v0 = crossbow ? BowBallistics.CROSSBOW_SPEED : BowBallistics.BOW_SPEED;
        Solution sol = aimSolution(tgt, v0, cfg);
        if (sol == null) {                         // no firing solution (too far / blocked arc) — keep holding, don't fire
            if (drawing) holdDraw(sol);
            else INPUTS.submit(InputRequest.noInput(this, getPriority()));
            return;
        }

        int charge = requiredCharge(crossbow, cfg);
        boolean los = hasLineOfSight(tgt, cfg);    // NEVER fire without a clear line to the target
        currentStrafe = cfg.mobileWhileEngaging ? computeStrafe(tgt, cfg) : 0;  // stay mobile / dodge while aiming

        if (crossbowReady) {                       // loaded crossbow: fire the instant we're on target + clear
            aim(sol);
            if (los && onTarget(sol)) {
                sendUse();                         // a single use fires the loaded bolt
                shotTaken(cfg);
            }
            return;                                // no LOS → stay loaded and keep tracking; pathing will reopen the shot
        }

        if (!drawing) {                            // begin the draw/charge only with a clear shot (don't charge at a wall)
            if (los) startDraw(sol);
            else aim(sol);                         // obstructed: just track the target, let movement reposition
            return;
        }

        // mid draw/charge: keep aiming and holding
        chargeTicks++;
        holdDraw(sol);
        if (chargeTicks < charge) return;          // not charged yet

        if (crossbow) {                            // charge complete → release to load, fire next tick (when clear)
            sendRelease();
            drawing = false;
            crossbowReady = true;
            return;
        }
        // bow: fire only with LOS, once aimed (or after a short grace so a fully-drawn bow never holds forever)
        if (los && (onTarget(sol) || chargeTicks >= charge + 10)) {
            sendRelease();
            shotTaken(cfg);
        }
    }

    // --- targeting -----------------------------------------------------------

    /**
     * Nearest valid hostile in the ranged band, <b>preferring one we have line-of-sight to</b>. A clear shot always
     * beats a closer obstructed one, so the bot never fixates on an enemy behind a wall — it retargets a shootable mob
     * and lets pathing reposition for the rest. Falls back to the nearest obstructed target (so protect still tracks it)
     * only when nothing is in the open; firing stays LOS-gated regardless.
     */
    private @Nullable EntityLiving findTarget(com.aquarius.util.config.Config.Client.Extra.AutoBow cfg) {
        double minSq = (double) cfg.minRange * cfg.minRange;
        double maxSq = (double) cfg.maxRange * cfg.maxRange;
        EntityLiving bestLos = null, bestAny = null;
        double bestLosSq = Double.MAX_VALUE, bestAnySq = Double.MAX_VALUE;
        for (var en : CACHE.getEntityCache().getEntities().values()) {
            if (!(en instanceof EntityLiving e) || e == CACHE.getPlayerCache().getThePlayer() || !e.isAlive()) continue;
            if (!validTarget(e, cfg)) continue;
            double dsq = CACHE.getPlayerCache().distanceSqToSelf(e);
            if (dsq < minSq || dsq > maxSq) continue;   // outside the ranged band
            if (dsq < bestAnySq) { bestAnySq = dsq; bestAny = e; }
            if (dsq < bestLosSq && hasLineOfSight(e, cfg)) { bestLosSq = dsq; bestLos = e; }
        }
        return bestLos != null ? bestLos : bestAny;
    }

    private boolean validTarget(EntityLiving e, com.aquarius.util.config.Config.Client.Extra.AutoBow cfg) {
        if (e instanceof EntityPlayer p) {
            if (!cfg.targetPlayers || p.isSelfPlayer()) return false;
            return !PLAYER_LISTS.getFriendsList().contains(p.getUuid())
                && !PLAYER_LISTS.getWhitelist().contains(p.getUuid())
                && !PLAYER_LISTS.getSpectatorWhitelist().contains(p.getUuid());
        }
        if (e instanceof EntityStandard s) {
            return cfg.targetHostileMobs && KillAura.isHostile(s.getEntityType());
        }
        return false;
    }

    // --- aiming --------------------------------------------------------------

    /** Compute the firing solution to the target's aim point, leading its motion by the arrow's flight time. */
    private @Nullable Solution aimSolution(EntityLiving tgt, double v0,
                                                         com.aquarius.util.config.Config.Client.Extra.AutoBow cfg) {
        updateVelocity(tgt);
        double ex = BOT.getX(), ey = BOT.getEyeY(), ez = BOT.getZ();
        double aimY = tgt.getY() + tgt.dimensions().getY() * cfg.aimHeightFraction;
        // first pass without lead, then refine the aim point by velocity × flight time
        Solution sol = BowBallistics.solve(ex, ey, ez, tgt.getX(), aimY, tgt.getZ(), v0, 200);
        if (cfg.leadTargets && sol != null) {
            for (int i = 0; i < 2; i++) {
                double t = sol.flightTicks();
                double px = tgt.getX() + velX * t;
                double pz = tgt.getZ() + velZ * t;
                double py = aimY + velY * t;
                Solution led = BowBallistics.solve(ex, ey, ez, px, py, pz, v0, 200);
                if (led == null) break;
                sol = led;
            }
        }
        return sol;
    }

    private void updateVelocity(EntityLiving tgt) {
        if (tgt.getEntityId() != velEntityId) {
            velEntityId = tgt.getEntityId();
            velX = velY = velZ = 0;
        } else {
            // exponential moving average of per-tick position delta
            double a = 0.5;
            velX = velX * (1 - a) + (tgt.getX() - lastTx) * a;
            velY = velY * (1 - a) + (tgt.getY() - lastTy) * a;
            velZ = velZ * (1 - a) + (tgt.getZ() - lastTz) * a;
        }
        lastTx = tgt.getX(); lastTy = tgt.getY(); lastTz = tgt.getZ();
    }

    /** True once the bot's actual rotation is within a few degrees of the firing solution. */
    private boolean onTarget(Solution sol) {
        float dy = Math.abs(MathHelper.wrapDegrees(BOT.getYaw() - sol.yaw()));
        float dp = Math.abs(BOT.getPitch() - sol.pitch());
        return dy <= 3.0f && dp <= 3.0f;
    }

    private boolean hasLineOfSight(EntityLiving tgt, com.aquarius.util.config.Config.Client.Extra.AutoBow cfg) {
        if (!cfg.requireLineOfSight) return true;
        double ex = BOT.getX(), ey = BOT.getEyeY(), ez = BOT.getZ();
        double aimY = tgt.getY() + tgt.dimensions().getY() * cfg.aimHeightFraction;
        double dx = tgt.getX() - ex, dy = aimY - ey, dz = tgt.getZ() - ez;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        // straight ray to the target center; a block hit before the target means the line is obstructed
        var ray = RaycastHelper.blockRaycastFromPos(ex, ey, ez, yaw, pitch, Math.max(0, dist - 0.5), false);
        return !ray.hit();
    }

    // --- dodge / kite movement ----------------------------------------------

    /**
     * Decide a strafe for this tick (-1 left, +1 right, 0 stay): sidestep the nearest incoming arrow if one is on a
     * collision course ({@code dodgeArrows}), else slowly kite back and forth so we're never a stationary target. The
     * chosen side is hazard-checked with the pathfinder's {@code canWalkThrough} so we never strafe off a ledge or into
     * lava; if a side is unsafe we try the other, and if neither is safe we hold still.
     */
    private int computeStrafe(EntityLiving tgt, com.aquarius.util.config.Config.Client.Extra.AutoBow cfg) {
        double bx = CACHE.getPlayerCache().getX(), bz = CACHE.getPlayerCache().getZ();
        float yawRad = (float) Math.toRadians(BOT.getYaw());
        double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);   // facing (horizontal)
        double rx = -fz, rz = fx;                               // bot's "right" world vector (pressingRight)

        int want = 0;
        if (cfg.dodgeArrows) {
            double[] arrow = nearestIncomingArrow(bx, bz);
            if (arrow != null) {
                // perpendicular to the arrow's horizontal velocity, toward the side the bot already sits on
                double avx = arrow[3], avz = arrow[5];
                double perpX = -avz, perpZ = avx;
                double relX = bx - arrow[0], relZ = bz - arrow[2];
                if (perpX * relX + perpZ * relZ < 0) { perpX = -perpX; perpZ = -perpZ; }
                want = (perpX * rx + perpZ * rz) >= 0 ? 1 : -1;  // map world dodge dir to left/right
            }
        }
        if (want == 0) {                                        // no incoming arrow → gentle kite
            if (--kiteFlipCtr <= 0) { kiteDir = -kiteDir; kiteFlipCtr = 20; }
            want = kiteDir;
        }
        // hazard-gate the chosen side, fall back to the other, else don't move
        if (safeToStrafe(want > 0 ? rx : -rx, want > 0 ? rz : -rz)) return want;
        if (safeToStrafe(want > 0 ? -rx : rx, want > 0 ? -rz : rz)) return -want;
        return 0;
    }

    /** True if stepping ~1 block along (dirX,dirZ) keeps solid footing and clear, non-hazard space (no ledge/lava). */
    private boolean safeToStrafe(double dirX, double dirZ) {
        int footY = (int) Math.floor(CACHE.getPlayerCache().getY());
        int dx = (int) Math.floor(CACHE.getPlayerCache().getX() + dirX);
        int dz = (int) Math.floor(CACHE.getPlayerCache().getZ() + dirZ);
        return MovementHelper.canWalkThrough(dx, footY, dz)
            && MovementHelper.canWalkThrough(dx, footY + 1, dz)
            && !MovementHelper.canWalkThrough(dx, footY - 1, dz);   // solid ground below the destination
    }

    /** Refresh per-arrow position+velocity estimates and prune arrows that despawned. */
    private void updateArrowTracking() {
        var entities = CACHE.getEntityCache().getEntities();
        for (var en : entities.values()) {
            var type = en.getEntityType();
            if (type != EntityType.ARROW && type != EntityType.SPECTRAL_ARROW) continue;
            int id = en.getEntityId();
            double[] prev = arrowTrack.get(id);
            double vx = 0, vy = 0, vz = 0;
            if (prev != null) { vx = en.getX() - prev[0]; vy = en.getY() - prev[1]; vz = en.getZ() - prev[2]; }
            arrowTrack.put(id, new double[]{ en.getX(), en.getY(), en.getZ(), vx, vy, vz });
        }
        for (Iterator<Integer> it = arrowTrack.keySet().iterator(); it.hasNext(); ) {
            if (!entities.containsKey(it.next())) it.remove();
        }
    }

    /** The nearest arrow on a near-collision course with the bot (closest-approach within a hit radius soon), or null. */
    private double @Nullable [] nearestIncomingArrow(double bx, double bz) {
        double by = CACHE.getPlayerCache().getY();
        double[] best = null;
        double bestT = Double.MAX_VALUE;
        for (double[] a : arrowTrack.values()) {
            double vx = a[3], vy = a[4], vz = a[5];
            double speedSq = vx * vx + vy * vy + vz * vz;
            if (speedSq < 0.09) continue;                       // resting / spent arrow (<0.3 b/t)
            double rx = bx - a[0], ry = by - a[1] + 1.0, rz = bz - a[2];  // aim at torso height
            double t = (rx * vx + ry * vy + rz * vz) / speedSq; // time of closest approach (ticks)
            if (t <= 0 || t > 25) continue;                     // moving away, or too far out to matter yet
            double cx = rx - vx * t, cy = ry - vy * t, cz = rz - vz * t;
            double missSq = cx * cx + cy * cy + cz * cz;
            if (missSq > 1.7 && t < bestT) continue;            // will miss by > ~1.3 blocks → not a threat
            if (missSq <= 1.7 && t < bestT) { bestT = t; best = a; }
        }
        return best;
    }

    // --- use / release plumbing ---------------------------------------------

    private void startDraw(Solution sol) {
        sendUse(sol).addInputExecutedListener(f -> { drawing = true; chargeTicks = 0; });
    }

    /** Submit a rotation-only input to keep aiming while the draw is held (does not re-trigger a use). */
    private void holdDraw(@Nullable Solution sol) {
        if (sol != null) aim(sol); else INPUTS.submit(InputRequest.noInput(this, getPriority()));
    }

    private void aim(Solution sol) {
        var b = InputRequest.builder().owner(this).yaw(sol.yaw()).pitch(sol.pitch()).priority(getPriority());
        if (currentStrafe != 0) {
            b.input(Input.builder()
                .pressingLeft(currentStrafe < 0)
                .pressingRight(currentStrafe > 0)
                .clickTarget(ClickTarget.None.INSTANCE)
                .clickRequiresRotation(false)
                .build());
        }
        INPUTS.submit(b.build());
    }

    /** Send a single right-click use (start a bow draw / crossbow charge, or fire a loaded crossbow). */
    private com.aquarius.feature.player.InputRequestFuture sendUse() {
        var hand = getHand() == null ? Hand.MAIN_HAND : getHand();
        return INPUTS.submit(InputRequest.builder()
            .owner(this)
            .input(Input.builder()
                .rightClick(true)
                .hand(hand)
                .clickTarget(ClickTarget.None.INSTANCE)
                .clickRequiresRotation(false)
                .build())
            .priority(getPriority())
            .build());
    }

    private com.aquarius.feature.player.InputRequestFuture sendUse(Solution sol) {
        var hand = getHand() == null ? Hand.MAIN_HAND : getHand();
        return INPUTS.submit(InputRequest.builder()
            .owner(this)
            .input(Input.builder()
                .rightClick(true)
                .hand(hand)
                .pressingLeft(currentStrafe < 0)        // keep moving as the draw starts
                .pressingRight(currentStrafe > 0)
                .clickTarget(ClickTarget.None.INSTANCE)
                .clickRequiresRotation(false)
                .build())
            .yaw(sol.yaw())
            .pitch(sol.pitch())
            .priority(getPriority())
            .build());
    }

    private void sendRelease() {
        sendClientPacketAsync(new ServerboundPlayerActionPacket(
            PlayerAction.RELEASE_USE_ITEM, 0, 0, 0, Direction.DOWN.mcpl(), 0));
    }

    private void cancelDraw() {
        sendRelease();              // release so we don't sit holding a charged bow with no target
        drawing = false;
        chargeTicks = 0;
    }

    private void shotTaken(com.aquarius.util.config.Config.Client.Extra.AutoBow cfg) {
        drawing = false;
        crossbowReady = false;
        chargeTicks = 0;
        cooldown = Math.max(1, cfg.postShotCooldownTicks);
    }

    // --- helpers -------------------------------------------------------------

    private int requiredCharge(boolean crossbow, com.aquarius.util.config.Config.Client.Extra.AutoBow cfg) {
        int base = crossbow ? cfg.crossbowChargeTicks : cfg.drawTicks;
        if (!cfg.tpsSync) return base;
        return MathHelper.ceilI(base * (20.0 / MathHelper.clamp(TPS.getTPSValue(), 1.0, 20.0)));
    }

    private boolean isHeldCrossbow() {
        var stack = CACHE.getPlayerCache().getEquipment(
            getHand() == Hand.OFF_HAND
                ? org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot.OFF_HAND
                : org.geysermc.mcprotocollib.protocol.data.game.entity.EquipmentSlot.MAIN_HAND);
        return stack != null && ItemRegistry.REGISTRY.get(stack.getId()) == ItemRegistry.CROSSBOW;
    }

    /** Whether the inventory holds at least one arrow (a survival bow needs ammo; crossbows too). */
    private boolean hasArrows() {
        for (ItemStack s : CACHE.getPlayerCache().getPlayerInventory()) {
            if (s == null) continue;
            var d = ItemRegistry.REGISTRY.get(s.getId());
            if (d == ItemRegistry.ARROW || d == ItemRegistry.TIPPED_ARROW || d == ItemRegistry.SPECTRAL_ARROW) return true;
        }
        return false;
    }

    @Override
    public boolean itemPredicate(final ItemStack itemStack) {
        var d = ItemRegistry.REGISTRY.get(itemStack.getId());
        if (d == null) return false;
        boolean isBow = d == ItemRegistry.BOW;
        boolean isCrossbow = d == ItemRegistry.CROSSBOW;
        if (!isBow && !isCrossbow) return false;
        if (!hasArrows()) return false;                 // no ammo → not a usable weapon
        if (CONFIG.client.extra.autoBow.preferCrossbow) return isCrossbow || isBow;
        return isBow || isCrossbow;
    }
}
