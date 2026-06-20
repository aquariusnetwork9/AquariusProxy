package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.cache.data.entity.EntityPlayer;
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
 * <p>Verbs (whisper them to the bot, optionally with args):
 * <ul>
 *   <li>{@code follow} — Baritone-follow you within {@code followRadius} blocks (default 32) and, by default, turn
 *       on KillAura so it fights what's near you.</li>
 *   <li>{@code come} — come to you: walk if you're within {@code comeFlyThreshold} blocks, else fly (ElytraTrip).
 *       Accepts explicit coords ({@code come <x> <z>} / {@code come <x> <y> <z>}) when you're out of render range.</li>
 *   <li>{@code patrol} — patrol the preset area (set via the panel / {@code wc patrol …}); drives SpawnPatrol.</li>
 *   <li>{@code mine} — mine the preset Corners box (set via the panel / {@code wc mine …}); drives AquariusMiner.</li>
 *   <li>{@code stop} — halt follow/patrol/mine/come (stops Baritone, the trip, and the modules it started).</li>
 *   <li>{@code help} — whisper back the verb list.</li>
 * </ul>
 *
 * <p><b>Authorization.</b> When the RBAC system is enabled, every verb requires {@code module.whispercontrol} plus
 * the underlying capability ({@code module.killaura} for follow, {@code module.pathfinder}/{@code module.elytrapilot}
 * for come, {@code module.spawnpatrol} for patrol, {@code module.aquariusminer} for mine). When RBAC is off, only the
 * account owner may command it (legacy default-safe behavior).
 */
public class WhisperControl extends Module {

    private static final String BASE_PERM = "module.whispercontrol";

    // remote-follow state: following a player by their reported position while they're out of render range
    private int tickCtr = 0;
    private UUID followUuid = null;
    private boolean followingNatively = false;
    private int[] lastRemoteTarget = null;

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
            case "follow" -> { if (authz(uuid, name, "module.killaura")) doFollow(uuid, name); else deny(name); }
            case "come"   -> { if (authz(uuid, name, "module.pathfinder")) doCome(uuid, name, tok); else deny(name); }
            case "patrol" -> { if (authz(uuid, name, "module.spawnpatrol")) doPatrol(name); else deny(name); }
            case "mine"   -> { if (authz(uuid, name, "module.aquariusminer")) doMine(name); else deny(name); }
            case "stop"   -> { if (authz(uuid, name, BASE_PERM)) doStop(name); else deny(name); }
            case "help"   -> { if (authz(uuid, name, BASE_PERM)) reply(name, "verbs: follow, come [x z], patrol, mine, stop"); else deny(name); }
            default       -> { /* not a control verb — ignore (lets AutoReply / chat relay handle it) */ }
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

    private void doFollow(UUID uuid, String name) {
        var wc = CONFIG.client.extra.whisperControl;
        boolean loaded = CACHE.getEntityCache().get(uuid) instanceof EntityPlayer;
        if (!loaded && reportedPos(uuid, name) == null) return;   // not in view + no fresh reported position (replied)
        CONFIG.client.extra.pathfinder.followRadius = wc.followRadius;   // leash around you (GoalNear range when remote)
        if (wc.followEnablesKillAura) {
            CONFIG.client.extra.killAura.enabled = true;
            MODULE.get(KillAura.class).syncEnabledFromConfig();
        }
        followUuid = uuid;
        lastRemoteTarget = null;
        String aura = wc.followEnablesKillAura ? ", killaura on)" : ")";
        if (CACHE.getEntityCache().get(uuid) instanceof EntityPlayer target) {
            followingNatively = true;
            BARITONE.follow(target);
            reply(name, "following you (radius " + wc.followRadius + aura);
        } else {
            followingNatively = false;                              // the tick re-paths toward your reported position
            reply(name, "following your reported position (radius " + wc.followRadius + aura);
        }
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
            // fly there via the trip planner (bot-relative routing decides direct vs nether)
            var ep = CONFIG.client.extra.elytraPilot;
            ep.tripTargetX = tx; ep.tripTargetY = ty; ep.tripTargetZ = tz;
            ep.tripTargetIsNether = false;
            ep.tripActiveRoute = "";
            ep.tripActive = true;
            MODULE.get(ElytraTrip.class).syncEnabledFromConfig();
            reply(name, "flying to you (" + (long) dist + "b) at " + tx + ", " + ty + ", " + tz);
        } else {
            BARITONE.pathTo(tx, ty, tz);
            reply(name, "walking to you (" + (long) dist + "b) at " + tx + ", " + ty + ", " + tz);
        }
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
        followUuid = null;
        followingNatively = false;
        lastRemoteTarget = null;
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

    // --- remote follow (reported positions) ----------------------------------

    /** Remote-follow loop: while following someone out of render range, re-path toward their latest reported
     *  position (~1 Hz); upgrade to a native entity-follow the moment they come into the bot's view. */
    private void onTick(ClientBotTick event) {
        if (followUuid == null) return;
        if (++tickCtr % 20 != 0) return;                    // ~1 Hz; Baritone runs the path itself every tick
        if (CACHE.getEntityCache().get(followUuid) instanceof EntityPlayer e) {
            if (!followingNatively) { followingNatively = true; BARITONE.follow(e); }  // came into view -> native follow
            return;
        }
        followingNatively = false;
        var wc = CONFIG.client.extra.whisperControl;
        if (!wc.useReportedPositions) return;
        var rp = PlayerLocations.get(followUuid, wc.reportedPosMaxAgeSeconds * 1000L);
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
