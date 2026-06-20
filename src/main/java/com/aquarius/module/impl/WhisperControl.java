package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.cache.data.entity.EntityLiving;
import com.aquarius.cache.data.entity.EntityPlayer;
import com.aquarius.cache.data.entity.EntityStandard;
import com.aquarius.event.chat.WhisperChatEvent;
import com.aquarius.event.client.ClientBotTick;
import com.aquarius.feature.location.PlayerLocations;
import com.aquarius.feature.pathfinder.goals.GoalNear;
import com.aquarius.feature.permissions.Subject;
import com.aquarius.feature.player.World;
import com.aquarius.mc.dimension.DimensionRegistry;
import com.aquarius.module.api.Module;
import com.aquarius.util.ChatUtil;
import com.aquarius.util.config.Config.Client.Extra.AquariusMiner.AreaMode;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.BARITONE;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.Globals.PERMISSIONS;

/**
 * Whisper-command control: authorized players can whisper the bot simple verbs and it acts. The handler hooks the
 * same {@link WhisperChatEvent} {@code AutoReply} uses, authorizes the sender, parses the leading verb, and drives
 * the existing modules — nothing here re-implements movement/combat/mining, it just wires them together.
 *
 * <p><b>Movement verbs</b> (whisper them to the bot, optionally with args):
 * <ul>
 *   <li>{@code protect} — guard you: leash within {@code followRadius} blocks, constantly scan that sphere around YOU
 *       for hostile mobs and path to the nearest to kill it (KillAura on by default), and swap a worn elytra for the
 *       best inventory chestplate once it's in range (restored on {@code stop}).</li>
 *   <li>{@code come} — come to you: walk if you're within {@code comeFlyThreshold} blocks, else fly (ElytraTrip).
 *       Accepts explicit coords ({@code come <x> <z>} / {@code come <x> <y> <z>}) when you're out of render range.</li>
 *   <li>{@code goto} — go to explicit coordinates ({@code goto <x> <y> <z>}). <b>The coordinates are sensitive</b>: the
 *       companion ProxyBridge mod captures them and sends them over the bot's HTTP API ({@code wc goto …}), so they
 *       never travel over Minecraft chat — see the mod's {@code /pb goto} and WhisperInterceptor.</li>
 *   <li>{@code patrol} — patrol the preset area (set via the panel / {@code wc patrol …}); drives SpawnPatrol.</li>
 *   <li>{@code mine} — mine the preset Corners box (set via the panel / {@code wc mine …}); drives AquariusMiner.</li>
 *   <li>{@code stop} — halt protect/patrol/mine/come (stops Baritone, the trip, and the modules it started; restores the elytra).</li>
 *   <li>{@code help} — whisper back the verb list.</li>
 * </ul>
 *
 * <p><b>Authorization.</b> When the RBAC system is enabled, every verb requires {@code module.whispercontrol} plus
 * the underlying capability ({@code module.killaura} for protect, {@code module.pathfinder}/{@code module.elytrapilot}
 * for come/goto, {@code module.spawnpatrol} for patrol, {@code module.aquariusminer} for mine). When RBAC is off, only
 * the account owner may command it (legacy default-safe behavior).
 */
public class WhisperControl extends Module {

    private static final String BASE_PERM = "module.whispercontrol";

    // protect state: guarding a player by leashing + scanning around them for hostiles
    private int tickCtr = 0;
    private UUID protectUuid = null;
    private int lastFollowId = -1;          // entity id Baritone is currently following (player or mob), -1 = none
    private int[] lastRemoteTarget = null;  // last reported-position we pathed to while the player was out of render
    private boolean swapAttempted = false;  // chestplate swap tried this session (do it once on entering range)
    private boolean elytraTradedOut = false;// an elytra was swapped out for a chestplate (restore it on stop)

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(WhisperChatEvent.class, this::handleWhisper),
            of(ClientBotTick.class, this::onTick)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.whisperControl.enabled;
    }

    private void handleWhisper(WhisperChatEvent event) {
        if (event.outgoing()) return;                       // our own outbound replies
        if (event.sender() == null || event.sender().getProfileId() == null) return;
        final UUID uuid = event.sender().getProfileId();
        final String name = event.sender().getName();
        if (name != null && name.equalsIgnoreCase(CONFIG.authentication.username)) return; // ignore self

        final String msg = event.message() == null ? "" : event.message().trim();
        if (msg.isEmpty()) return;
        final String[] tok = msg.split("\\s+");
        final String verb = tok[0].toLowerCase(Locale.ROOT);

        switch (verb) {
            case "protect" -> { if (authz(uuid, name, "module.killaura")) doProtect(uuid, name); else deny(name); }
            case "come"    -> { if (authz(uuid, name, "module.pathfinder")) doCome(uuid, name, tok); else deny(name); }
            case "goto"    -> { if (authz(uuid, name, "module.pathfinder")) doGoto(name, tok); else deny(name); }
            case "patrol"  -> { if (authz(uuid, name, "module.spawnpatrol")) doPatrol(name); else deny(name); }
            case "mine"    -> { if (authz(uuid, name, "module.aquariusminer")) doMine(name); else deny(name); }
            case "stop"    -> { if (authz(uuid, name, BASE_PERM)) doStop(name); else deny(name); }
            case "help"    -> { if (authz(uuid, name, BASE_PERM)) reply(name, "verbs: protect, come [x z], goto <x y z>, patrol, mine, stop"); else deny(name); }
            default        -> { /* not a control verb — ignore (lets AutoReply / chat relay handle it) */ }
        }
    }

    // --- authorization -------------------------------------------------------

    /** RBAC: require the base perm AND the verb's capability perm. RBAC off: owner-only. */
    private boolean authz(UUID uuid, String name, String verbPerm) {
        if (PERMISSIONS.isEnabled()) {
            Subject s = PERMISSIONS.resolve(uuid, name);
            return PERMISSIONS.allows(s, BASE_PERM) && PERMISSIONS.allows(s, verbPerm);
        }
        return PERMISSIONS.isOwner(uuid);
    }

    private void deny(String name) {
        reply(name, "you are not authorized to command me");
    }

    // --- verbs ---------------------------------------------------------------

    private void doProtect(UUID uuid, String name) {
        var wc = CONFIG.client.extra.whisperControl;
        boolean loaded = CACHE.getEntityCache().get(uuid) instanceof EntityPlayer;
        if (!loaded && reportedPos(uuid, name) == null) return;   // not in view + no fresh reported position (replied)
        CONFIG.client.extra.pathfinder.followRadius = wc.followRadius;  // leash distance for the native follow
        if (wc.followEnablesKillAura) {
            CONFIG.client.extra.killAura.targetHostileMobs = true;     // protect = clear hostiles around you
            CONFIG.client.extra.killAura.enabled = true;
            MODULE.get(KillAura.class).syncEnabledFromConfig();
        }
        protectUuid = uuid;
        lastFollowId = -1;
        lastRemoteTarget = null;
        swapAttempted = false;
        elytraTradedOut = false;
        String aura = wc.followEnablesKillAura ? ", killaura on" : "";
        reply(name, (loaded ? "protecting you" : "coming to protect you") + " (radius " + wc.followRadius + aura + ")");
    }

    private void doCome(UUID uuid, String name, String[] tok) {
        var wc = CONFIG.client.extra.whisperControl;
        Integer tx = null, ty = null, tz = null;
        // explicit coords: "come <x> <z>" or "come <x> <y> <z>"
        try {
            if (tok.length >= 4) { tx = Integer.parseInt(tok[1]); ty = Integer.parseInt(tok[2]); tz = Integer.parseInt(tok[3]); }
            else if (tok.length == 3) { tx = Integer.parseInt(tok[1]); tz = Integer.parseInt(tok[2]); }
        } catch (NumberFormatException nf) {
            reply(name, "usage: come  |  come <x> <z>  |  come <x> <y> <z>"); return;
        }
        if (tx == null) {                                   // no coords — resolve your loaded entity, else reported pos
            if (CACHE.getEntityCache().get(uuid) instanceof EntityPlayer target) {
                tx = (int) Math.round(target.getX());
                ty = (int) Math.round(target.getY());
                tz = (int) Math.round(target.getZ());
            } else {
                int[] rp = reportedPos(uuid, name);
                if (rp == null) return;                     // reportedPos already replied why
                tx = rp[0]; ty = rp[1]; tz = rp[2];
            }
        }
        if (ty == null) ty = (int) Math.round(CACHE.getPlayerCache().getY());

        double dist = Math.hypot(tx - CACHE.getPlayerCache().getX(), tz - CACHE.getPlayerCache().getZ());
        if (dist > wc.comeFlyThreshold) {
            flyTo(tx, ty, tz);
            reply(name, "flying to you (" + (long) dist + "b)");
        } else {
            BARITONE.pathTo(tx, ty, tz);
            reply(name, "walking to you (" + (long) dist + "b)");
        }
    }

    /** {@code goto <x> <y> <z>} — go to explicit coordinates. Replies <b>without</b> echoing the coordinates (they are
     *  sensitive; the mod routes them over the HTTP API, not chat). The actual movement lives in {@link #goTo}. */
    private void doGoto(String name, String[] tok) {
        Integer x = null, y = null, z = null;
        try {
            if (tok.length >= 4) { x = Integer.parseInt(tok[1]); y = Integer.parseInt(tok[2]); z = Integer.parseInt(tok[3]); }
        } catch (NumberFormatException nf) { x = null; }
        if (x == null) { reply(name, "usage: goto <x> <y> <z>"); return; }
        reply(name, goTo(x, y, z));
    }

    /**
     * Move the bot to explicit coordinates — walk within {@code comeFlyThreshold}, fly (ElytraTrip) beyond it. Public
     * so the {@code wc goto} command can drive it from the HTTP API (the off-chat path the ProxyBridge mod uses, so the
     * coordinates never hit Minecraft chat). The returned status deliberately omits the coordinates.
     * @return a short, coordinate-free status line
     */
    public String goTo(int x, int y, int z) {
        double dist = Math.hypot(x - CACHE.getPlayerCache().getX(), z - CACHE.getPlayerCache().getZ());
        if (dist > CONFIG.client.extra.whisperControl.comeFlyThreshold) {
            flyTo(x, y, z);
            return "flying to target (" + (long) dist + "b)";
        }
        BARITONE.pathTo(x, y, z);
        return "walking to target (" + (long) dist + "b)";
    }

    /** Hand a destination to the trip planner (bot-relative routing decides direct vs nether). */
    private void flyTo(int x, int y, int z) {
        var ep = CONFIG.client.extra.elytraPilot;
        ep.tripTargetX = x; ep.tripTargetY = y; ep.tripTargetZ = z;
        ep.tripTargetIsNether = false;
        ep.tripActiveRoute = "";
        ep.tripActive = true;
        MODULE.get(ElytraTrip.class).syncEnabledFromConfig();
    }

    private void doPatrol(String name) {
        var wc = CONFIG.client.extra.whisperControl;
        if (!wc.patrolConfigured) { reply(name, "no patrol area set — set one with the panel (wc panel) or `wc patrol <x> <y> <z> <range>`"); return; }
        var sp = CONFIG.client.extra.spawnPatrol;
        sp.goalX = wc.patrolCenterX; sp.goalY = wc.patrolCenterY; sp.goalZ = wc.patrolCenterZ;
        sp.maxPatrolRange = wc.patrolRange;
        sp.enabled = true;
        MODULE.get(SpawnPatrol.class).syncEnabledFromConfig();
        reply(name, "patrolling " + wc.patrolCenterX + ", " + wc.patrolCenterZ + " (range " + wc.patrolRange + ")");
    }

    private void doMine(String name) {
        var wc = CONFIG.client.extra.whisperControl;
        if (!wc.mineConfigured) { reply(name, "no mine area set — set one with the panel (wc panel) or `wc mine <x1> <z1> <x2> <z2> <minY> <maxY>`"); return; }
        var am = CONFIG.client.extra.aquariusMiner;
        am.areaMode = AreaMode.Corners;
        am.corner1X = wc.mineCorner1X; am.corner1Z = wc.mineCorner1Z;
        am.corner2X = wc.mineCorner2X; am.corner2Z = wc.mineCorner2Z;
        am.minY = wc.mineMinY; am.maxY = wc.mineMaxY;
        am.enabled = true;
        MODULE.get(AquariusMiner.class).syncEnabledFromConfig();
        reply(name, "mining box " + wc.mineCorner1X + "," + wc.mineCorner1Z + " -> " + wc.mineCorner2X + "," + wc.mineCorner2Z
            + " (y " + wc.mineMinY + ".." + wc.mineMaxY + ")");
    }

    private void doStop(String name) {
        protectUuid = null;
        lastFollowId = -1;
        lastRemoteTarget = null;
        swapAttempted = false;
        if (elytraTradedOut) {                              // we swapped an elytra out for combat — put it back on
            MODULE.get(AutoArmor.class).equipElytraFromInventory();
            elytraTradedOut = false;
        }
        BARITONE.stop();
        CONFIG.client.extra.elytraPilot.tripActive = false;
        MODULE.get(ElytraTrip.class).syncEnabledFromConfig();
        CONFIG.client.extra.spawnPatrol.enabled = false;
        MODULE.get(SpawnPatrol.class).syncEnabledFromConfig();
        CONFIG.client.extra.aquariusMiner.enabled = false;
        MODULE.get(AquariusMiner.class).syncEnabledFromConfig();
        if (CONFIG.client.extra.whisperControl.followEnablesKillAura) {
            CONFIG.client.extra.killAura.enabled = false;
            MODULE.get(KillAura.class).syncEnabledFromConfig();
        }
        reply(name, "stopped");
    }

    // --- protect loop --------------------------------------------------------

    /**
     * Protect loop (~2 Hz). While guarding someone:
     * <ul>
     *   <li>in render range — swap a worn elytra for a chestplate once (combat prep), scan a {@code followRadius} sphere
     *       around the player for hostile mobs, and Baritone-follow the nearest (KillAura kills it on contact); when the
     *       sphere is clear, leash back to the player;</li>
     *   <li>out of render range — re-path toward their latest reported position (~once it moves), keeping the elytra on
     *       so the bot can still travel; combat resumes the moment they come into view.</li>
     * </ul>
     */
    private void onTick(ClientBotTick event) {
        if (protectUuid == null) return;
        if (++tickCtr % 10 != 0) return;                    // ~2 Hz; Baritone runs the path itself every tick
        var wc = CONFIG.client.extra.whisperControl;

        if (CACHE.getEntityCache().get(protectUuid) instanceof EntityPlayer target) {
            // combat prep: trade the elytra for a chestplate once, now that we're in range
            if (wc.protectSwapToChestplate && !swapAttempted) {
                swapAttempted = true;
                elytraTradedOut = MODULE.get(AutoArmor.class).equipChestplateOverElytra();
            }
            double px = target.getX(), py = target.getY(), pz = target.getZ();
            EntityLiving hostile = nearestHostile(px, py, pz, wc.followRadius);
            EntityLiving leashTarget = hostile != null ? hostile : target;
            int desiredId = leashTarget.getEntityId();
            if (desiredId != lastFollowId || !BARITONE.getFollowProcess().isActive()) {
                BARITONE.follow(leashTarget);              // chase the mob, or leash back to the player when clear
                lastFollowId = desiredId;
            }
            return;
        }

        // out of render: re-path toward the player's reported position (keep the elytra, no combat possible here)
        lastFollowId = -1;
        if (!wc.useReportedPositions) return;
        var rp = PlayerLocations.get(protectUuid, wc.reportedPosMaxAgeSeconds * 1000L);
        if (rp.isEmpty()) return;                           // stale — hold position, don't thrash
        var p = rp.get();
        if (!p.dimension().isEmpty() && !p.dimension().equals(botDimension())) return; // different dimension
        int x = (int) Math.round(p.x()), y = (int) Math.round(p.y()), z = (int) Math.round(p.z());
        int moveThresh = Math.max(4, wc.followRadius / 2);
        if (lastRemoteTarget != null
            && Math.abs(lastRemoteTarget[0] - x) + Math.abs(lastRemoteTarget[2] - z) < moveThresh) return; // not moved enough
        lastRemoteTarget = new int[]{x, y, z};
        BARITONE.pathTo(new GoalNear(x, y, z, Math.max(1, wc.followRadius * wc.followRadius)));
    }

    /** Nearest alive hostile mob within {@code radius} blocks of (x,y,z), or null. Uses KillAura's hostile table. */
    private EntityLiving nearestHostile(double x, double y, double z, int radius) {
        EntityLiving best = null;
        double bestSq = (double) radius * radius;
        for (var e : CACHE.getEntityCache().getEntities().values()) {
            if (!(e instanceof EntityStandard mob)) continue;
            if (!mob.isAlive()) continue;
            if (!KillAura.isHostile(mob.getEntityType())) continue;
            double dx = mob.getX() - x, dy = mob.getY() - y, dz = mob.getZ() - z;
            double dsq = dx * dx + dy * dy + dz * dz;
            if (dsq <= bestSq) { bestSq = dsq; best = mob; }
        }
        return best;
    }

    /** A fresh, same-dimension reported position {x,y,z}, or null after replying why (off / stale / cross-dimension). */
    private int[] reportedPos(UUID uuid, String name) {
        var wc = CONFIG.client.extra.whisperControl;
        if (!wc.useReportedPositions) { reply(name, "I can't see you — send coords: come <x> <z>"); return null; }
        var rp = PlayerLocations.get(uuid, wc.reportedPosMaxAgeSeconds * 1000L);
        if (rp.isEmpty()) { reply(name, "I can't see you and have no recent position from you — send coords, or have your client report position"); return null; }
        var p = rp.get();
        if (!p.dimension().isEmpty() && !p.dimension().equals(botDimension())) {
            reply(name, "you're in " + p.dimension() + ", I'm in " + botDimension() + " — can't cross dimensions"); return null;
        }
        return new int[]{ (int) Math.round(p.x()), (int) Math.round(p.y()), (int) Math.round(p.z()) };
    }

    private static String botDimension() {
        var d = World.getCurrentDimension();
        if (d == DimensionRegistry.THE_NETHER.get()) return "the_nether";
        if (d == DimensionRegistry.THE_END.get()) return "the_end";
        return "overworld";
    }

    // --- helpers -------------------------------------------------------------

    private void reply(String name, String message) {
        if (name == null || name.isBlank()) return;
        sendClientPacketAsync(ChatUtil.getWhisperChatPacket(name, "[bot] " + message));
    }
}
