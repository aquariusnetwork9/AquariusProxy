package com.aquarius.command.impl;

import com.aquarius.feature.elytra.Route;
import com.aquarius.module.impl.ElytraPilot;
import com.aquarius.module.impl.ElytraTrip;
import com.aquarius.util.config.Config.Client.Extra.ElytraPilot.HighwayDir;
import com.aquarius.util.config.Config.Client.Extra.ElytraPilot.BounceKick;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.ArrayList;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.LongArgumentType.getLong;
import static com.mojang.brigadier.arguments.LongArgumentType.longArg;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.DISCORD;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;

public class ElytraPilotCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("elytrapilot")
            .category(CommandCategory.MODULE)
            .aliases("fly")
            .description("""
                Autopilot elytra flight. Deploys, steers, fires fireworks, and lands.
                Needs an elytra worn + firework rockets in the hotbar. Short alias: .fly
                """)
            .usageLines(
                "on/off",
                "to <x> <z>            (fly to coords using the climb/glide profile, then land)",
                "heading <degrees>     (free-fly a compass bearing until stopped)",
                "ceiling <y>           (top of the climb — ascend to here on fireworks)",
                "floor <y>             (bottom of the glide — climb again when you sink to here)",
                "glidepitch <deg>      (nose-down pitch while gliding; ~+2 = max distance)",
                "climbpitch <deg>      (nose-up pitch while firework-climbing; ~42 = max height)",
                "glideratio <r>        (blocks forward per block down when gliding; sets descent lead)",
                "groundy <y>           (approx ground height at the target, for descent timing)",
                "boostspeed <v>        (fire a firework when speed drops below v blocks/tick)",
                "interval <ticks>      (max ticks between fireworks)",
                "lookahead <blocks>    (terrain avoidance scan distance)",
                "arrive <blocks>       (how close to the target counts as arrived)",
                "maxflight <ticks>     (hard per-flight time cap before aborting; 20 ticks/s, 36000 = 30 min)",
                "descend <blocks>      (distance from target to start descending)",
                "takeoff <on/off>      (on = pulse-jump to deploy; off = assume airborne/ledge)",
                "swap <on/off>         (auto-swap in a fresh elytra mid-flight when the worn one wears out)",
                "swapdur <n>           (swap the worn elytra at this remaining durability; max is 432)",
                "sparedur <n>          (min remaining durability for an inventory elytra to count as a spare)",
                "clearance <blocks>    (min height above ground before a flight-dropping swap is attempted)",
                "ebounce <on/off>      (bounce-highway mode: skip along a flat road, no fireworks)",
                "road <y>              (the flat road's surface Y, for ebounce)",
                "maxspeed <bps>        (speed cap in blocks/sec; 2b2t limit is 40 — keep ~38)",
                "bouncepitch <deg>     (e-bounce flat-road skim pitch; ~0 matches a real capture)",
                "bouncejump <on/off>   (e-bounce: allow the launch/recover jump; the bounce itself is a jumpless skip)",
                "bouncejumpspeed <bps> (e-bounce: only jump to launch/recover below this speed; above it the skip carries it low)",
                "bouncehop <on/off>    (e-bounce: proactively glide over terrain ahead; off = bounce through + walk-past real walls)",
                "bouncestall <ticks>   (ticks of near-zero bounce speed before giving up to a walk-past)",
                "redeploy <ticks>      (min ticks between START_FALL_FLYING re-sends during e-bounce)",
                "bouncedebug <on/off>  (log per-tick bounce telemetry: y / speeds / pitch / fall-flying, for tuning)",
                "bouncekick <firework|sprint>  (how the bounce reaches speed: firework boost, or RUN-to-start then ramp)",
                "bouncekickpitch <deg> (firework-kickstart dive pitch; shallow so the boost goes horizontal)",
                "bounceinject <bpt>    (HOLD maintenance cap: max horizontal speed added per tick; steady need ~0.02)",
                "bouncerunspeed <bps>  (sprint-start: ground speed to reach by running before deploying)",
                "bouncerunramp <bps>   (sprint-start: how fast injected speed ramps up from the running start)",
                "highway <dir>         (follow a 2b2t nether highway from 0,0: N/S/E/W/NE/SE/NW/SW; sets ebounce + y120)",
                "pass <on/off>         (on obstacle: settle + Baritone past it along the axis, then resume bounce)",
                "passahead <blocks>    (how far along the axis to aim the Baritone bypass)",
                "reroute <on/off>      (terrain-aware approach: re-route around terrain to a safe landing)",
                "rerouteangle <deg>    (max heading deviation the re-route may use; up to ~70)",
                "pathclearance <n>     (vertical clearance the glide path keeps above terrain)",
                "landsearch <blocks>   (radius to search around the target for a clear landing spot)",
                "baritoneland <on/off> (walk the last leg with Baritone if covered/indoors/underground)",
                "climbmargin <n>       (stop boosting this many blocks below the ceiling; saves fireworks)",
                "landcut <n>           (cut the glide + drop in within this many blocks of the ground)",
                "divefloor <y>         (over target: dive hard above this Y to shed excess altitude, land gently below)",
                "divepitch <deg>       (nose-down pitch for the aggressive over-target spiral dive)",
                "cruisescale <on/off>  (overworld/End: distance-scale the cruise ceiling — climb ONCE to the height the leg needs, then glide all the way in)",
                "cruiseglide <r>       (glide ratio at the managed speed band; sizes the climb, descent lead, undershoot re-climb, and firework estimate)",
                "cruiseceil <y>        (hard cap on the distance-scaled cruise altitude; total rockets are cap-independent)",
                "cruisespeed <min> <max> <hard>  (overworld/End glide speed band b/s: hold min-max, never overspeed/thrust past hard)",
                "cruisetrim <deg>      (pitch nudge ± off glidepitch used to hold the speed band)",
                "cruisebrake <deg>     (nose-up pitch to brake when the glide overspeeds past the hard cap)",
                "cruiseband <n>        (legacy sawtooth amplitude — unused by the single climb+glide profile)",
                "climbfire <ticks>     (patient climb cadence — one rocket then coast; ~120 = 6s, matches efficient manual flight)",
                "altperrocket <n>      (blocks of altitude per flight-3 rocket at climbpitch; feeds the firework estimate)",
                "estimatefw <on/off>   (size the pre-flight firework requirement to the overworld-direct trip distance)",
                "fwmargin <m>          (safety multiplier on the estimated rocket count)",
                "trip <x> <z> [y]      (plan a journey to OVERWORLD coords: direct if within ~100k of spawn, else via the nether)",
                "trip nether <x> <z>   (destination IS in the nether: enter a portal, fly to the exact coords, land there)",
                "trip highways <on/off>(nether transit leg: e-bounce a highway vs fly open-nether straight to the target)",
                "trip radius <blocks>  (overworld-direct cutoff: targets within this of 0,0 fly direct, beyond it route via the nether)",
                "trip acquire <blocks> (within this perpendicular distance of the chosen highway, Baritone walks onto the road instead of nether-flying)",
                "trip route new <name> ow <x> <y> <z> | nether   (create a saved multi-leg route; ends overworld at x,y,z or in the nether)",
                "trip route leg <name> <ride|fly> coord <nx> <nz> [roadY]   (append a leg to a nether endpoint; ride=e-bounce a road, fly=open-nether)",
                "trip route leg <name> <ride|fly> head <dir> <dist> [roadY] (append a leg by heading+distance from the last waypoint)",
                "trip route run|show|del|dellast <name> / trip route list   (run / inspect / manage saved routes)",
                "trip off              (cancel/reset an in-progress trip)",
                "trip startonconnect <on/off> (armed trip starts/resumes when the bot enters the world)",
                "panel                 (post an interactive trip panel to Discord: dimension dropdown + typed X/Y/Z modal + launch)",
                "netherceiling <y>     (hard altitude cap in the nether; never climb above it / into bedrock)",
                "nethercruise <y>      (preferred open-nether flight altitude; holds ~this Y and dodges in 3D)",
                "native <on/off>       (native nether-pathfinder: full-route planning through UNLOADED chunks via seed terrain-gen)",
                "seed <long>           (world seed for native nether routing; default = 2b2t's nether seed)",
                "netherfrontier <slow> <hold>  (highway e-bounce: blocks of loaded terrain ahead below which to coast / brake)",
                "hoppitch <deg>        (climb angle when gliding over an on-road obstacle)",
                "solver <on/off>       (simulation flight solver: fly only pitches whose simulated future is collision-free)",
                "simticks <n>          (ticks of elytra physics simulated per candidate pitch; Baritone uses 20)",
                "pitchrange <deg>      (pitch sweep around the direct line, ± degrees; Baritone uses 25)",
                "boostbelow <bps>      (solver path: fire a rocket when speed drops below this and not already boosted)",
                "setbackhold <ticks>   (after a server position setback, hold all rockets this long; never fight a rubberband)",
                "relaunchpitch <deg>   (relaunch pitch when a CEILING is above: shallow ~-20 = slide forward out from under it)",
                "relaunchpitchup <deg> (relaunch pitch when OPEN SKY is above: steep ~-75 = punch straight up out of a lava ocean)"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("elytrapilot")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.enabled = getToggle(c, "toggle");
                MODULE.get(ElytraPilot.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("ElytraPilot " + toggleStrCaps(CONFIG.client.extra.elytraPilot.enabled));
            }))
            .then(literal("restart").executes(c -> {
                // Clean re-arm in ONE command (== off then on): re-enables if needed, resets state, and re-enters the
                // TAKEOFF/BOUNCE phase even if already enabled. Use after changing config while armed — `fly on` alone
                // is a no-op when already enabled, so it would NOT pick up the change.
                MODULE.get(ElytraPilot.class).beginFlight();
                c.getSource().getEmbed()
                    .title("ElytraPilot restarted")
                    .description("Re-armed cleanly (off→on in one shot) — reset state + re-entered the phase. Picks up config changed while it was already enabled.");
            }))
            .then(literal("to")
                .then(argument("x", integer())
                    .then(argument("z", integer()).executes(c -> {
                        CONFIG.client.extra.elytraPilot.hasTarget = true;
                        CONFIG.client.extra.elytraPilot.highway = false;
                        CONFIG.client.extra.elytraPilot.targetX = getInteger(c, "x");
                        CONFIG.client.extra.elytraPilot.targetZ = getInteger(c, "z");
                        c.getSource().getEmbed()
                            .title("ElytraPilot target")
                            .description("Flying to " + CONFIG.client.extra.elytraPilot.targetX + ", "
                                + CONFIG.client.extra.elytraPilot.targetZ);
                    }))))
            .then(literal("heading").then(argument("degrees", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.hasTarget = false;
                CONFIG.client.extra.elytraPilot.highway = false;
                CONFIG.client.extra.elytraPilot.heading = (float) getDouble(c, "degrees");
                c.getSource().getEmbed()
                    .title("ElytraPilot heading")
                    .description("Free-flying heading " + CONFIG.client.extra.elytraPilot.heading);
            })))
            .then(literal("ceiling").then(argument("y", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.glideCeilingY = getInteger(c, "y");
                c.getSource().getEmbed().title("ElytraPilot glide ceiling = " + CONFIG.client.extra.elytraPilot.glideCeilingY);
            })))
            .then(literal("floor").then(argument("y", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.glideFloorY = getInteger(c, "y");
                c.getSource().getEmbed().title("ElytraPilot glide floor = " + CONFIG.client.extra.elytraPilot.glideFloorY);
            })))
            .then(literal("glidepitch").then(argument("degrees", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.glidePitch = (float) getDouble(c, "degrees");
                c.getSource().getEmbed().title("ElytraPilot glide pitch = " + CONFIG.client.extra.elytraPilot.glidePitch);
            })))
            .then(literal("climbpitch").then(argument("degrees", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.climbPitch = (float) getDouble(c, "degrees");
                c.getSource().getEmbed().title("ElytraPilot climb pitch = " + CONFIG.client.extra.elytraPilot.climbPitch);
            })))
            .then(literal("glideratio").then(argument("ratio", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.glideRatio = getDouble(c, "ratio");
                c.getSource().getEmbed().title("ElytraPilot glide ratio = " + CONFIG.client.extra.elytraPilot.glideRatio);
            })))
            .then(literal("groundy").then(argument("y", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.approxGroundY = getInteger(c, "y");
                c.getSource().getEmbed().title("ElytraPilot approx ground Y = " + CONFIG.client.extra.elytraPilot.approxGroundY);
            })))
            .then(literal("boostspeed").then(argument("v", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.minBoostSpeed = getDouble(c, "v");
                c.getSource().getEmbed().title("ElytraPilot boost speed = " + CONFIG.client.extra.elytraPilot.minBoostSpeed);
            })))
            .then(literal("interval").then(argument("ticks", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.maxBoostIntervalTicks = getInteger(c, "ticks");
                c.getSource().getEmbed().title("ElytraPilot boost interval = " + CONFIG.client.extra.elytraPilot.maxBoostIntervalTicks);
            })))
            .then(literal("lookahead").then(argument("blocks", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.lookAheadBlocks = getInteger(c, "blocks");
                c.getSource().getEmbed().title("ElytraPilot look-ahead = " + CONFIG.client.extra.elytraPilot.lookAheadBlocks);
            })))
            .then(literal("arrive").then(argument("blocks", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.arriveRadius = getInteger(c, "blocks");
                c.getSource().getEmbed().title("ElytraPilot arrive radius = " + CONFIG.client.extra.elytraPilot.arriveRadius);
            })))
            .then(literal("maxflight").then(argument("ticks", integer(20)).executes(c -> {
                CONFIG.client.extra.elytraPilot.maxFlightTicks = getInteger(c, "ticks");
                c.getSource().getEmbed().title("ElytraPilot max flight time = " + CONFIG.client.extra.elytraPilot.maxFlightTicks
                    + " ticks (" + String.format("%.1f", CONFIG.client.extra.elytraPilot.maxFlightTicks / 20.0 / 60.0) + " min)");
            })))
            .then(literal("descend").then(argument("blocks", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.descendRadius = getInteger(c, "blocks");
                c.getSource().getEmbed().title("ElytraPilot descend radius = " + CONFIG.client.extra.elytraPilot.descendRadius);
            })))
            .then(literal("takeoff").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.doubleJumpTakeoff = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("ElytraPilot double-jump takeoff " + toggleStrCaps(CONFIG.client.extra.elytraPilot.doubleJumpTakeoff));
            })))
            .then(literal("swap").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.swapElytra = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("ElytraPilot elytra swap " + toggleStrCaps(CONFIG.client.extra.elytraPilot.swapElytra));
            })))
            .then(literal("swapdur").then(argument("durability", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.elytraMinDurability = getInteger(c, "durability");
                c.getSource().getEmbed().title("ElytraPilot swap-at durability = " + CONFIG.client.extra.elytraPilot.elytraMinDurability);
            })))
            .then(literal("sparedur").then(argument("durability", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.freshElytraMinDurability = getInteger(c, "durability");
                c.getSource().getEmbed().title("ElytraPilot min spare durability = " + CONFIG.client.extra.elytraPilot.freshElytraMinDurability);
            })))
            .then(literal("clearance").then(argument("blocks", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.minSwapClearance = getInteger(c, "blocks");
                c.getSource().getEmbed().title("ElytraPilot min swap clearance = " + CONFIG.client.extra.elytraPilot.minSwapClearance);
            })))
            .then(literal("ebounce").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.ebounce = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("ElytraPilot e-bounce mode " + toggleStrCaps(CONFIG.client.extra.elytraPilot.ebounce))
                    .description("Flat-road bounce highway, no fireworks. Set 'road <y>' to the road surface and a heading/target.");
            })))
            .then(literal("road").then(argument("y", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.roadY = getInteger(c, "y");
                c.getSource().getEmbed().title("ElytraPilot road Y = " + CONFIG.client.extra.elytraPilot.roadY);
            })))
            .then(literal("maxspeed").then(argument("bps", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.maxSpeed = getDouble(c, "bps");
                c.getSource().getEmbed().title("ElytraPilot max speed = " + CONFIG.client.extra.elytraPilot.maxSpeed + " b/s");
            })))
            .then(literal("bouncepitch").then(argument("degrees", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.bouncePitch = (float) getDouble(c, "degrees");
                c.getSource().getEmbed().title("ElytraPilot e-bounce pitch = " + CONFIG.client.extra.elytraPilot.bouncePitch + "°")
                    .description("Flat-road skim pitch. A real capture bounces at ~0°; +pitch noses down (was glidePitch +2, which popped the bot up).");
            })))
            .then(literal("bouncedive").then(argument("degrees", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceDivePitch = (float) getDouble(c, "degrees");
                c.getSource().getEmbed().title("ElytraPilot e-bounce dive pitch = " + CONFIG.client.extra.elytraPilot.bounceDivePitch + "°")
                    .description("Nose-down pitch applied only above roadY+1, to dive back to the road for the next bounce instead of floating into a hover. Higher = dives harder.");
            })))
            .then(literal("bouncestallspeed").then(argument("bps", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceStallSpeed = getDouble(c, "bps");
                c.getSource().getEmbed().title("ElytraPilot e-bounce stall speed = " + CONFIG.client.extra.elytraPilot.bounceStallSpeed + " b/s")
                    .description("Below this forward speed a bounce tick counts as stalled; after bounceStallLimit such ticks the bot routes around the obstacle (Baritone) instead of bouncing into it.");
            })))
            .then(literal("bouncedivegain").then(argument("degPerBlock", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceDiveGain = getDouble(c, "degPerBlock");
                c.getSource().getEmbed().title("ElytraPilot e-bounce dive gain = " + CONFIG.client.extra.elytraPilot.bounceDiveGain + " deg/block")
                    .description("Degrees of extra nose-down per block above the dive height (proportional, smooth ramp). Higher caps the apex tighter; too high gets abrupt enough to desync.");
            })))
            .then(literal("bounceredeployvy").then(argument("vy", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceRedeployMaxVy = getDouble(c, "vy");
                c.getSource().getEmbed().title("ElytraPilot e-bounce redeploy maxVy = " + CONFIG.client.extra.elytraPilot.bounceRedeployMaxVy)
                    .description("Only re-deploy the elytra once vertical speed drops below this (deploy on the way DOWN). Keeps the rise ballistic = low apex (no ceiling hits). Raise toward 0.4 to deploy throughout the rise.");
            })))
            .then(literal("bounceclearground").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceClearOnGround = getToggle(c, "toggle");
                c.getSource().getEmbed().title("ElytraPilot e-bounce clear-ff-on-ground " + toggleStrCaps(CONFIG.client.extra.elytraPilot.bounceClearOnGround))
                    .description("Clear fall-flying locally on the ground tick to match Grim's prediction (it clears ff when it sees onGround=true). Removes the 1-tick drag divergence that sets the bounce back at ~24 b/s.");
            })))
            .then(literal("resupply").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.resupplyFromEchest = getToggle(c, "toggle");
                c.getSource().getEmbed().title("ElytraPilot elytra resupply " + toggleStrCaps(CONFIG.client.extra.elytraPilot.resupplyFromEchest))
                    .description("Restock fresh elytra spares from the carried ender-chest kit when they run low (needs a carried ender chest, a silk pickaxe, and a stocked kit shulker). Never touches the worn elytra.");
            })))
            .then(literal("resupplyspares").then(argument("n", integer(0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.resupplySpareThreshold = getInteger(c, "n");
                c.getSource().getEmbed().title("ElytraPilot resupply when fresh spares < " + CONFIG.client.extra.elytraPilot.resupplySpareThreshold);
            })))
            .then(literal("resupplycount").then(argument("n", integer(1)).executes(c -> {
                CONFIG.client.extra.elytraPilot.resupplyElytraCount = getInteger(c, "n");
                c.getSource().getEmbed().title("ElytraPilot resupply target = " + CONFIG.client.extra.elytraPilot.resupplyElytraCount + " fresh elytras");
            })))
            .then(literal("bouncedeploy").then(argument("blocks", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceDeployHeight = getDouble(c, "blocks");
                c.getSource().getEmbed().title("ElytraPilot e-bounce deploy height = " + CONFIG.client.extra.elytraPilot.bounceDeployHeight + " above road")
                    .description("Blocks above the road the bot must rise before re-deploying the elytra. Too low = server rejects + desync setback; too high = bleeds speed staying ballistic.");
            })))
            .then(literal("bouncediveheight").then(argument("blocks", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceDiveHeight = getDouble(c, "blocks");
                c.getSource().getEmbed().title("ElytraPilot e-bounce dive height = " + CONFIG.client.extra.elytraPilot.bounceDiveHeight + " above road")
                    .description("Height above the road at which the nose-down dive kicks in. Lower = tighter, lower-apex bounce (less speed bled to altitude).");
            })))
            .then(literal("bouncejump").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceJump = getToggle(c, "toggle");
                c.getSource().getEmbed().title("ElytraPilot e-bounce launch jump " + toggleStrCaps(CONFIG.client.extra.elytraPilot.bounceJump))
                    .description("Allows the launch/recover jump. The bounce itself is a jumpless skip; off = never jump (only works if already gliding).");
            })))
            .then(literal("bouncejumpspeed").then(argument("bps", doubleArg(0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceJumpBelowSpeed = getDouble(c, "bps");
                c.getSource().getEmbed().title("ElytraPilot e-bounce jump-below speed = " + CONFIG.client.extra.elytraPilot.bounceJumpBelowSpeed + " b/s")
                    .description("Only jump to launch/recover below this speed; above it the elytra-skip carries the bot low + builds speed (jumping every cycle bounced ~2 blocks into the walls).");
            })))
            .then(literal("bouncehop").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceHopObstacles = getToggle(c, "toggle");
                c.getSource().getEmbed().title("ElytraPilot e-bounce proactive hop " + toggleStrCaps(CONFIG.client.extra.elytraPilot.bounceHopObstacles))
                    .description("Off = bounce through minor clutter (real walls still handled by the stall walk-past). On = glide over terrain ahead (can false-trigger on ceilings).");
            })))
            .then(literal("bouncestall").then(argument("ticks", integer(1)).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceStallLimit = getInteger(c, "ticks");
                c.getSource().getEmbed().title("ElytraPilot e-bounce stall limit = " + CONFIG.client.extra.elytraPilot.bounceStallLimit + " ticks")
                    .description("Ticks of near-zero bounce speed before giving up to a walk-past. Raise it if the bounce needs longer to build speed from a standstill.");
            })))
            .then(literal("redeploy").then(argument("ticks", integer(1)).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceRedeployTicks = getInteger(c, "ticks");
                c.getSource().getEmbed().title("ElytraPilot e-bounce redeploy spacing = " + CONFIG.client.extra.elytraPilot.bounceRedeployTicks + " ticks");
            })))
            .then(literal("bouncedebug").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceDebug = getToggle(c, "toggle");
                c.getSource().getEmbed().title("ElytraPilot e-bounce telemetry " + toggleStrCaps(CONFIG.client.extra.elytraPilot.bounceDebug))
                    .description("Logs per-tick y / vertical+horizontal speed / pitch / fall-flying while bouncing, for tuning. Spammy — turn off after.");
            })))
            .then(literal("bouncekick").then(argument("mode", word()).executes(c -> {
                String m = getString(c, "mode").toLowerCase();
                var cfg = CONFIG.client.extra.elytraPilot;
                if (m.startsWith("f")) cfg.bounceKickStart = BounceKick.Firework;
                else if (m.startsWith("sy")) cfg.bounceKickStart = BounceKick.Synth;
                else if (m.startsWith("s")) cfg.bounceKickStart = BounceKick.Sprint;
                else { c.getSource().getEmbed().title("Usage: fly bouncekick <firework|sprint|synth>"); return; }
                String desc = switch (cfg.bounceKickStart) {
                    case Firework -> "Boost to speed with real fireworks, then hold by injecting only the tiny drag top-up.";
                    case Sprint -> "RUN on the ground to get moving first (no fireworks), then deploy + ramp the injected speed up from that moving state.";
                    case Synth -> "Byte-for-byte: synthesize the exact MovePlayerPos stream (parabola + held fall-flying), bypassing physics, ramped 0->target. No fireworks.";
                };
                c.getSource().getEmbed().title("ElytraPilot e-bounce kickstart = " + cfg.bounceKickStart).description(desc);
            })))
            .then(literal("bouncekickpitch").then(argument("degrees", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceKickPitch = (float) getDouble(c, "degrees");
                c.getSource().getEmbed().title("ElytraPilot e-bounce kickstart pitch = " + CONFIG.client.extra.elytraPilot.bounceKickPitch + "°")
                    .description("Firework-kickstart dive pitch: shallow so the boost goes horizontal down the road, not into the floor.");
            })))
            .then(literal("bounceinject").then(argument("bpt", doubleArg(0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceMaxInjectPerTick = getDouble(c, "bpt");
                c.getSource().getEmbed().title("ElytraPilot e-bounce max inject = " + CONFIG.client.extra.elytraPilot.bounceMaxInjectPerTick + " b/tick")
                    .description("HOLD maintenance cap: max horizontal speed added per tick (steady-state need ~0.02). Caps a glitch from snapping into a cold-start jump.");
            })))
            .then(literal("bouncerunspeed").then(argument("bps", doubleArg(0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceRunStartSpeed = getDouble(c, "bps");
                c.getSource().getEmbed().title("ElytraPilot e-bounce run-start speed = " + CONFIG.client.extra.elytraPilot.bounceRunStartSpeed + " b/s")
                    .description("Sprint-start: ground speed to reach by running before deploying + ramping (ground sprint tops out ~5.6 b/s).");
            })))
            .then(literal("bouncerunramp").then(argument("bps", doubleArg(0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceRunRampPerSec = getDouble(c, "bps");
                c.getSource().getEmbed().title("ElytraPilot e-bounce run ramp = " + CONFIG.client.extra.elytraPilot.bounceRunRampPerSec + " b/s per s")
                    .description("Sprint-start: how fast the injected speed ramps up from the running start. Lower = gentler, more likely within Grim's tolerance.");
            })))
            .then(literal("bouncespeed").then(argument("bps", doubleArg(0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceSpeed = getDouble(c, "bps");
                c.getSource().getEmbed().title("ElytraPilot e-bounce target speed = " + CONFIG.client.extra.elytraPilot.bounceSpeed + " b/s")
                    .description("Target the KICKSTART builds to and HOLD maintains. 2b2t's ceiling is ~40 — keep a margin.");
            })))
            .then(literal("bouncerestoreticks").then(argument("n", integer(1)).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceRestoreTicks = getInteger(c, "n");
                c.getSource().getEmbed().title("ElytraPilot e-bounce restore ticks = " + CONFIG.client.extra.elytraPilot.bounceRestoreTicks)
                    .description("HOLD bleed-and-restore: spread the per-cycle restore over this many ticks after liftoff (1=sharp, higher=gentler per-tick deviation).");
            })))
            .then(literal("bouncerestoremax").then(argument("bpt", doubleArg(0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceRestoreMax = getDouble(c, "bpt");
                c.getSource().getEmbed().title("ElytraPilot e-bounce restore max = " + CONFIG.client.extra.elytraPilot.bounceRestoreMax + " b/tick")
                    .description("HOLD bleed-and-restore: hard cap on the total per-cycle restore (a real cycle's drag loss is ~0.18).");
            })))
            .then(literal("bouncehover").then(argument("low", doubleArg()).then(argument("high", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceHoverLow = getDouble(c, "low");
                CONFIG.client.extra.elytraPilot.bounceHoverHigh = getDouble(c, "high");
                c.getSource().getEmbed().title("ElytraPilot airborne-glide hover band = roadY+" + CONFIG.client.extra.elytraPilot.bounceHoverLow + " .. roadY+" + CONFIG.client.extra.elytraPilot.bounceHoverHigh)
                    .description("Vertical porpoise band above the road. The glide climbs when below the low edge, sinks when above the high edge; never touches ground.");
            }))))
            .then(literal("bounceclimbvel").then(argument("v", doubleArg(0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceClimbVel = getDouble(c, "v");
                c.getSource().getEmbed().title("ElytraPilot airborne-glide climb vel = " + CONFIG.client.extra.elytraPilot.bounceClimbVel)
                    .description("Vertical velocity set during the climb phase (after gravity ~0.03 nets a gentle +0.01/tick climb).");
            })))
            .then(literal("bounceboost").then(argument("factor", doubleArg(0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceBoostFactor = getDouble(c, "factor");
                c.getSource().getEmbed().title("ElytraPilot airborne-glide boost factor = " + CONFIG.client.extra.elytraPilot.bounceBoostFactor)
                    .description("Horizontal injection smoothing: v += (target-v)*factor each tick. Captured client uses ~0.5 (ramps 0->target over ~8 ticks).");
            })))
            .then(literal("bounceholdenter").then(argument("frac", doubleArg(0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceHoldEnterFrac = getDouble(c, "frac");
                c.getSource().getEmbed().title("ElytraPilot e-bounce HOLD-enter = " + CONFIG.client.extra.elytraPilot.bounceHoldEnterFrac + " x target")
                    .description("Hand off from firework KICKSTART to maintenance inject-HOLD once speed reaches this fraction of target.");
            })))
            .then(literal("bounceholdexit").then(argument("frac", doubleArg(0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.bounceHoldExitFrac = getDouble(c, "frac");
                c.getSource().getEmbed().title("ElytraPilot e-bounce HOLD-exit = " + CONFIG.client.extra.elytraPilot.bounceHoldExitFrac + " x target")
                    .description("Drop back to firework KICKSTART if HOLD speed falls below this fraction of target.");
            })))
            .then(literal("highway").then(argument("dir", word()).executes(c -> {
                HighwayDir dir;
                try {
                    dir = HighwayDir.valueOf(getString(c, "dir").toUpperCase());
                } catch (IllegalArgumentException e) {
                    c.getSource().getEmbed().title("Error").description("Direction must be one of: N S E W NE SE NW SW");
                    return ERROR;
                }
                var cfg = CONFIG.client.extra.elytraPilot;
                cfg.highway = true;
                cfg.highwayDir = dir;
                cfg.ebounce = true;
                cfg.roadY = 120;
                cfg.hasTarget = false;
                cfg.roadAnchorX = 0; cfg.roadAnchorZ = 0; cfg.roadDirX = 0; cfg.roadDirZ = 0;  // clean spawn highway
                c.getSource().getEmbed()
                    .title("ElytraPilot highway " + dir)
                    .description("E-bounce along the " + dir + " nether highway (y" + cfg.roadY + "). Run /fly on to start.");
                return OK;
            })))
            .then(literal("pass").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.passObstacles = getToggle(c, "toggle");
                c.getSource().getEmbed().title("ElytraPilot obstacle passing " + toggleStrCaps(CONFIG.client.extra.elytraPilot.passObstacles));
            })))
            .then(literal("passahead").then(argument("blocks", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.passAheadBlocks = getInteger(c, "blocks");
                c.getSource().getEmbed().title("ElytraPilot pass-ahead = " + CONFIG.client.extra.elytraPilot.passAheadBlocks);
            })))
            .then(literal("reroute").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.reroute = getToggle(c, "toggle");
                c.getSource().getEmbed().title("ElytraPilot re-route " + toggleStrCaps(CONFIG.client.extra.elytraPilot.reroute));
            })))
            .then(literal("rerouteangle").then(argument("degrees", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.maxRerouteDeg = getDouble(c, "degrees");
                c.getSource().getEmbed().title("ElytraPilot max re-route angle = " + CONFIG.client.extra.elytraPilot.maxRerouteDeg);
            })))
            .then(literal("pathclearance").then(argument("blocks", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.pathClearance = getInteger(c, "blocks");
                c.getSource().getEmbed().title("ElytraPilot path clearance = " + CONFIG.client.extra.elytraPilot.pathClearance);
            })))
            .then(literal("landsearch").then(argument("blocks", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.landingSearchRadius = getInteger(c, "blocks");
                c.getSource().getEmbed().title("ElytraPilot landing search radius = " + CONFIG.client.extra.elytraPilot.landingSearchRadius);
            })))
            .then(literal("baritoneland").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.baritoneLand = getToggle(c, "toggle");
                c.getSource().getEmbed().title("ElytraPilot Baritone walk-in " + toggleStrCaps(CONFIG.client.extra.elytraPilot.baritoneLand));
            })))
            .then(literal("climbmargin").then(argument("blocks", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.climbStopMargin = getInteger(c, "blocks");
                c.getSource().getEmbed().title("ElytraPilot climb-stop margin = " + CONFIG.client.extra.elytraPilot.climbStopMargin);
            })))
            .then(literal("landcut").then(argument("blocks", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.landCutClearance = getInteger(c, "blocks");
                c.getSource().getEmbed().title("ElytraPilot land-cut clearance = " + CONFIG.client.extra.elytraPilot.landCutClearance);
            })))
            .then(literal("divefloor").then(argument("y", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.landDiveFloorY = getInteger(c, "y");
                c.getSource().getEmbed().title("ElytraPilot land dive floor = y" + CONFIG.client.extra.elytraPilot.landDiveFloorY)
                    .description("Over the target, dive hard (no speed cap) above this Y to shed excess altitude fast, then land gently below it. Keep above max terrain (~y320).");
            })))
            .then(literal("divepitch").then(argument("degrees", doubleArg(0, 89)).executes(c -> {
                CONFIG.client.extra.elytraPilot.landDivePitch = (float) getDouble(c, "degrees");
                c.getSource().getEmbed().title("ElytraPilot land dive pitch = " + CONFIG.client.extra.elytraPilot.landDivePitch + "° (nose-down spiral dive)");
            })))
            .then(literal("panel").executes(c -> {
                boolean posted = DISCORD.openTripPanel();
                c.getSource().getEmbed()
                    .title(posted ? "Trip panel posted to Discord" : "Discord bot not running")
                    .description(posted
                        ? "In Discord: pick the dimension (dropdown), Set Coordinates (typed X/Y/Z modal), toggle Highways/Gear-up, then Launch."
                        : "Enable the Discord bot to use the interactive trip panel.");
            }))
            .then(literal("cruisescale").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.cruiseScaleCeiling = getToggle(c, "toggle");
                c.getSource().getEmbed().title("ElytraPilot distance-scaled cruise ceiling " + toggleStrCaps(CONFIG.client.extra.elytraPilot.cruiseScaleCeiling))
                    .description("Overworld/End: climb ONCE to the height the leg needs (dist/glideratio, capped), then a single continuous glide to the target. Off = fixed ceiling/floor band.");
            })))
            .then(literal("cruiseglide").then(argument("ratio", doubleArg(0.5)).executes(c -> {
                CONFIG.client.extra.elytraPilot.cruiseGlideRatio = getDouble(c, "ratio");
                c.getSource().getEmbed().title("ElytraPilot cruise glide ratio = " + CONFIG.client.extra.elytraPilot.cruiseGlideRatio
                    + " (sizes the climb, the descent lead, the undershoot re-climb, and the firework estimate)");
            })))
            .then(literal("cruiseceil").then(argument("y", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.cruiseCeilingMaxY = getInteger(c, "y");
                c.getSource().getEmbed().title("ElytraPilot cruise ceiling cap = y" + CONFIG.client.extra.elytraPilot.cruiseCeilingMaxY
                    + " (total rockets are cap-independent; this only bounds altitude/fall risk)");
            })))
            .then(literal("cruisespeed")
                .then(argument("min", doubleArg(1))
                .then(argument("max", doubleArg(1))
                .then(argument("hard", doubleArg(1)).executes(c -> {
                    var cfg = CONFIG.client.extra.elytraPilot;
                    cfg.cruiseSpeedSoftMin = getDouble(c, "min");
                    cfg.cruiseSpeedSoftMax = getDouble(c, "max");
                    cfg.cruiseSpeedHardMax = getDouble(c, "hard");
                    c.getSource().getEmbed().title("ElytraPilot cruise speed band = " + cfg.cruiseSpeedSoftMin + "-" + cfg.cruiseSpeedSoftMax
                        + " b/s (hard cap " + cfg.cruiseSpeedHardMax + ")")
                        .description("Overworld/End glide trims pitch to hold the soft band; never thrusts or overspeed-glides past the hard cap.");
                })))))
            .then(literal("cruisetrim").then(argument("deg", doubleArg(0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.cruiseGlideTrimDeg = (float) getDouble(c, "deg");
                c.getSource().getEmbed().title("ElytraPilot cruise glide trim = ±" + CONFIG.client.extra.elytraPilot.cruiseGlideTrimDeg
                    + "° (pitch nudge off glidepitch to hold the speed band)");
            })))
            .then(literal("cruisebrake").then(argument("deg", doubleArg(0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.cruiseBrakePitch = (float) getDouble(c, "deg");
                c.getSource().getEmbed().title("ElytraPilot cruise brake pitch = " + CONFIG.client.extra.elytraPilot.cruiseBrakePitch
                    + "° nose-up (brake when the glide overspeeds past the hard cap)");
            })))
            .then(literal("cruiseband").then(argument("blocks", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.cruiseBandHeight = getInteger(c, "blocks");
                c.getSource().getEmbed().title("ElytraPilot cruise band = " + CONFIG.client.extra.elytraPilot.cruiseBandHeight
                    + " blocks (legacy sawtooth amplitude — unused by the single climb+glide profile)");
            })))
            .then(literal("climbfire").then(argument("ticks", integer(1)).executes(c -> {
                CONFIG.client.extra.elytraPilot.cruiseClimbFireIntervalTicks = getInteger(c, "ticks");
                c.getSource().getEmbed().title("ElytraPilot patient climb cadence = " + CONFIG.client.extra.elytraPilot.cruiseClimbFireIntervalTicks
                    + " ticks/rocket (" + String.format("%.1f", CONFIG.client.extra.elytraPilot.cruiseClimbFireIntervalTicks / 20.0) + "s)");
            })))
            .then(literal("altperrocket").then(argument("blocks", doubleArg(1)).executes(c -> {
                CONFIG.client.extra.elytraPilot.climbAltPerRocket = getDouble(c, "blocks");
                c.getSource().getEmbed().title("ElytraPilot altitude per rocket = " + CONFIG.client.extra.elytraPilot.climbAltPerRocket
                    + "b (flight-3 @ climbpitch; feeds the firework estimate)");
            })))
            .then(literal("estimatefw").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.tripEstimateFireworks = getToggle(c, "toggle");
                c.getSource().getEmbed().title("ElytraPilot trip firework estimate " + toggleStrCaps(CONFIG.client.extra.elytraPilot.tripEstimateFireworks))
                    .description("On = size the pre-flight firework requirement to the (overworld-direct) trip distance; off = flat 1-stack minimum.");
            })))
            .then(literal("fwmargin").then(argument("mult", doubleArg(1.0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.fireworkSafetyMargin = getDouble(c, "mult");
                c.getSource().getEmbed().title("ElytraPilot firework safety margin = " + CONFIG.client.extra.elytraPilot.fireworkSafetyMargin + "×");
            })))
            .then(literal("netherceiling").then(argument("y", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.netherCeilingY = getInteger(c, "y");
                c.getSource().getEmbed().title("ElytraPilot nether ceiling = " + CONFIG.client.extra.elytraPilot.netherCeilingY);
            })))
            .then(literal("nethercruise").then(argument("y", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.netherCruiseY = getInteger(c, "y");
                c.getSource().getEmbed().title("ElytraPilot nether cruise altitude = " + CONFIG.client.extra.elytraPilot.netherCruiseY);
            })))
            .then(literal("native").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.nativeRouting = getToggle(c, "toggle");
                c.getSource().getEmbed().title("ElytraPilot native nether routing "
                    + toggleStrCaps(CONFIG.client.extra.elytraPilot.nativeRouting));
            })))
            .then(literal("seed").then(argument("seed", longArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.netherSeed = getLong(c, "seed");
                c.getSource().getEmbed().title("ElytraPilot nether seed = " + CONFIG.client.extra.elytraPilot.netherSeed)
                    .description("The native router's context regenerates on the next route request.");
            })))
            .then(literal("netherfrontier").then(argument("slow", integer()).then(argument("hold", integer()).executes(c -> {
                CONFIG.client.extra.elytraPilot.netherFrontierSlow = getInteger(c, "slow");
                CONFIG.client.extra.elytraPilot.netherFrontierHold = getInteger(c, "hold");
                c.getSource().getEmbed().title("ElytraPilot nether frontier: coast < " + CONFIG.client.extra.elytraPilot.netherFrontierSlow
                    + ", brake < " + CONFIG.client.extra.elytraPilot.netherFrontierHold + " blocks of loaded terrain ahead");
            }))))
            .then(literal("hoppitch").then(argument("degrees", doubleArg()).executes(c -> {
                CONFIG.client.extra.elytraPilot.hopPitch = (float) getDouble(c, "degrees");
                c.getSource().getEmbed().title("ElytraPilot hop pitch = " + CONFIG.client.extra.elytraPilot.hopPitch);
            })))
            .then(literal("solver").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.elytraPilot.solver = getToggle(c, "toggle");
                c.getSource().getEmbed().title("ElytraPilot simulation solver "
                    + toggleStrCaps(CONFIG.client.extra.elytraPilot.solver));
            })))
            .then(literal("simticks").then(argument("ticks", integer(5, 40)).executes(c -> {
                CONFIG.client.extra.elytraPilot.solverSimTicks = getInteger(c, "ticks");
                c.getSource().getEmbed().title("ElytraPilot solver simulation = "
                    + CONFIG.client.extra.elytraPilot.solverSimTicks + " ticks per candidate pitch");
            })))
            .then(literal("pitchrange").then(argument("degrees", integer(5, 88)).executes(c -> {
                CONFIG.client.extra.elytraPilot.solverPitchRange = getInteger(c, "degrees");
                c.getSource().getEmbed().title("ElytraPilot solver pitch sweep = ±"
                    + CONFIG.client.extra.elytraPilot.solverPitchRange + "°");
            })))
            .then(literal("boostbelow").then(argument("bps", doubleArg(1.0)).executes(c -> {
                CONFIG.client.extra.elytraPilot.boostBelowSpeed = getDouble(c, "bps");
                c.getSource().getEmbed().title("ElytraPilot solver boost threshold = "
                    + CONFIG.client.extra.elytraPilot.boostBelowSpeed + " b/s (rocket when slower + not boosted)");
            })))
            .then(literal("setbackhold").then(argument("ticks", integer(0, 200)).executes(c -> {
                CONFIG.client.extra.elytraPilot.setbackHoldTicks = getInteger(c, "ticks");
                c.getSource().getEmbed().title("ElytraPilot setback rocket hold = "
                    + CONFIG.client.extra.elytraPilot.setbackHoldTicks + " ticks");
            })))
            .then(literal("relaunchpitch").then(argument("degrees", doubleArg(-89, 89)).executes(c -> {
                CONFIG.client.extra.elytraPilot.relaunchPitch = (float) getDouble(c, "degrees");
                c.getSource().getEmbed().title("ElytraPilot relaunch pitch (ceiling/forward) = " + CONFIG.client.extra.elytraPilot.relaunchPitch
                    + "° (negative = nose up; shallow = forward out from under an overhang)");
            })))
            .then(literal("relaunchpitchup").then(argument("degrees", doubleArg(-89, 89)).executes(c -> {
                CONFIG.client.extra.elytraPilot.relaunchPitchUp = (float) getDouble(c, "degrees");
                c.getSource().getEmbed().title("ElytraPilot relaunch pitch (open/up) = " + CONFIG.client.extra.elytraPilot.relaunchPitchUp
                    + "° (steep = punch straight up out of a lava ocean / open ground)");
            })))
            .then(literal("trip")
                .then(literal("highways").then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.elytraPilot.tripUseHighways = getToggle(c, "toggle");
                    c.getSource().getEmbed().title("ElytraPilot trip highways " + toggleStrCaps(CONFIG.client.extra.elytraPilot.tripUseHighways));
                })))
                .then(literal("gearup").then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.elytraPilot.tripGearUp = getToggle(c, "toggle");
                    c.getSource().getEmbed().title("ElytraPilot trip gear-up " + toggleStrCaps(CONFIG.client.extra.elytraPilot.tripGearUp))
                        .description("When on, a naked bot first Regears the flight kit (elytra+fireworks+armor+totem) from a nearby ender chest before flying.");
                })))
                .then(literal("startonconnect").then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.elytraPilot.tripStartOnConnect = getToggle(c, "toggle");
                    c.getSource().getEmbed().title("ElytraPilot trip start-on-connect " + toggleStrCaps(CONFIG.client.extra.elytraPilot.tripStartOnConnect))
                        .description("When on, an armed trip starts/resumes when the bot enters the world — set it while logged out and it flies on login (also resumes after a disconnect).");
                })))
                .then(literal("radius").then(argument("blocks", integer(0)).executes(c -> {
                    CONFIG.client.extra.elytraPilot.spawnRegionRadius = getInteger(c, "blocks");
                    c.getSource().getEmbed().title("ElytraPilot trip overworld-direct radius = " + CONFIG.client.extra.elytraPilot.spawnRegionRadius + "b")
                        .description("Destinations within this radius of 0,0 fly overworld-direct; beyond it the trip routes via the nether (8:1 scale).");
                })))
                .then(literal("acquire").then(argument("blocks", integer(0)).executes(c -> {
                    CONFIG.client.extra.elytraPilot.highwayAcquireRadius = getInteger(c, "blocks");
                    c.getSource().getEmbed().title("ElytraPilot highway acquire radius = " + CONFIG.client.extra.elytraPilot.highwayAcquireRadius + "b")
                        .description("Within this perpendicular distance of the chosen highway, the bot Baritone-walks onto the road (climb to y120 / tunnel / center) instead of nether-flying.");
                })))
                .then(literal("route")
                    .then(literal("new").then(argument("name", word())
                        .then(literal("ow").then(argument("x", integer()).then(argument("y", integer()).then(argument("z", integer()).executes(c -> {
                            return newRoute(c.getSource(), getString(c, "name"), false, getInteger(c, "x"), getInteger(c, "y"), getInteger(c, "z")); })))))
                        .then(literal("nether").executes(c -> {
                            return newRoute(c.getSource(), getString(c, "name"), true, 0, 64, 0); }))))
                    .then(literal("leg").then(argument("name", word()).then(argument("mode", word())
                        .then(literal("coord").then(argument("nx", integer()).then(argument("nz", integer())
                            .executes(c -> { return addLeg(c.getSource(), getString(c, "name"), isRide(c), getInteger(c, "nx"), getInteger(c, "nz"), 120); })
                            .then(argument("roadY", integer()).executes(c -> {
                                return addLeg(c.getSource(), getString(c, "name"), isRide(c), getInteger(c, "nx"), getInteger(c, "nz"), getInteger(c, "roadY")); })))))
                        .then(literal("head").then(argument("dir", word()).then(argument("dist", integer(1))
                            .executes(c -> { return addHeadLeg(c.getSource(), getString(c, "name"), isRide(c), getString(c, "dir"), getInteger(c, "dist"), 120); })
                            .then(argument("roadY", integer()).executes(c -> {
                                return addHeadLeg(c.getSource(), getString(c, "name"), isRide(c), getString(c, "dir"), getInteger(c, "dist"), getInteger(c, "roadY")); }))))))))
                    .then(literal("dellast").then(argument("name", word()).executes(c -> { return dellastLeg(c.getSource(), getString(c, "name")); })))
                    .then(literal("del").then(argument("name", word()).executes(c -> { return delRoute(c.getSource(), getString(c, "name")); })))
                    .then(literal("list").executes(c -> { return listRoutes(c.getSource()); }))
                    .then(literal("show").then(argument("name", word()).executes(c -> { return showRoute(c.getSource(), getString(c, "name")); })))
                    .then(literal("run").then(argument("name", word()).executes(c -> { return runRoute(c.getSource(), getString(c, "name")); }))))
                .then(literal("off").executes(c -> {
                    CONFIG.client.extra.elytraPilot.tripActive = false;
                    MODULE.get(ElytraTrip.class).syncEnabledFromConfig();
                    c.getSource().getEmbed().title("ElytraPilot trip cancelled");
                }))
                .then(literal("nether")
                    .then(argument("x", integer())
                        .then(argument("z", integer()).executes(c -> {
                            return startNetherTrip(c.getSource(), getInteger(c, "x"), getInteger(c, "z"));
                        }))))
                .then(argument("x", integer())
                    .then(argument("z", integer())
                        .executes(c -> { return startTrip(c.getSource(), getInteger(c, "x"), 64, getInteger(c, "z")); })
                        .then(argument("y", integer()).executes(c -> {
                            return startTrip(c.getSource(), getInteger(c, "x"), getInteger(c, "y"), getInteger(c, "z"));
                        })))));
    }

    private int startTrip(CommandContext ctx, int x, int y, int z) {
        var cfg = CONFIG.client.extra.elytraPilot;
        cfg.tripTargetX = x;
        cfg.tripTargetY = y;
        cfg.tripTargetZ = z;
        cfg.tripTargetIsNether = false;
        cfg.tripActiveRoute = "";          // a plain coord trip, not a saved route
        cfg.tripActive = true;
        MODULE.get(ElytraTrip.class).syncEnabledFromConfig();
        double dist = Math.hypot(x, z);
        ctx.getEmbed()
            .title("ElytraPilot trip started")
            .description("Destination " + x + ", " + y + ", " + z
                + (dist <= cfg.spawnRegionRadius
                    ? " — overworld-direct (within the spawn region)."
                    : " — via the nether highways (" + (long) dist + "b out)."));
        return OK;
    }

    private int startNetherTrip(CommandContext ctx, int x, int z) {
        var cfg = CONFIG.client.extra.elytraPilot;
        cfg.tripTargetX = x;
        cfg.tripTargetY = 64;
        cfg.tripTargetZ = z;
        cfg.tripTargetIsNether = true;
        cfg.tripActiveRoute = "";          // a plain coord trip, not a saved route
        cfg.tripActive = true;
        MODULE.get(ElytraTrip.class).syncEnabledFromConfig();
        ctx.getEmbed()
            .title("ElytraPilot trip started")
            .description("Nether destination " + x + ", " + z
                + " — entering a portal if needed, then flying to the exact coords and landing there.");
        return OK;
    }

    // --- saved multi-leg routes ---

    private int newRoute(CommandContext ctx, String name, boolean endInNether, int x, int y, int z) {
        CONFIG.client.extra.elytraPilot.tripRoutes.put(name, new Route(name, endInNether, x, y, z, new ArrayList<>()));
        ctx.getEmbed().title("Route '" + name + "' created")
            .description(endInNether
                ? "Ends in the nether at the last leg's coords. Add legs: `fly trip route leg " + name + " ...`."
                : "Ends overworld at " + x + ", " + y + ", " + z + ". Add legs: `fly trip route leg " + name + " ...`.");
        return OK;
    }

    private int addLeg(CommandContext ctx, String name, boolean ride, int nx, int nz, int roadY) {
        Route r = CONFIG.client.extra.elytraPilot.tripRoutes.get(name);
        if (r == null) {
            ctx.getEmbed().title("No such route '" + name + "'").description("Create it first: `fly trip route new " + name + " ...`");
            return ERROR;
        }
        r.legs().add(new Route.Leg(ride, nx, nz, roadY));
        ctx.getEmbed().title("Route '" + name + "' — leg " + r.legs().size() + " added")
            .description((ride ? "RIDE" : "FLY") + " to nether " + nx + ", " + nz + (ride ? " (road y" + roadY + ")" : ""));
        return OK;
    }

    private int addHeadLeg(CommandContext ctx, String name, boolean ride, String dirStr, int dist, int roadY) {
        Route r = CONFIG.client.extra.elytraPilot.tripRoutes.get(name);
        if (r == null) {
            ctx.getEmbed().title("No such route '" + name + "'").description("Create it first: `fly trip route new " + name + " ...`");
            return ERROR;
        }
        HighwayDir dir;
        try {
            dir = HighwayDir.valueOf(dirStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getEmbed().title("Bad direction").description("Direction must be one of: N S E W NE SE NW SW");
            return ERROR;
        }
        int px = r.legs().isEmpty() ? 0 : r.legs().get(r.legs().size() - 1).x();
        int pz = r.legs().isEmpty() ? 0 : r.legs().get(r.legs().size() - 1).z();
        double[] u = unitVec(dir);
        return addLeg(ctx, name, ride, px + (int) Math.round(u[0] * dist), pz + (int) Math.round(u[1] * dist), roadY);
    }

    private int dellastLeg(CommandContext ctx, String name) {
        Route r = CONFIG.client.extra.elytraPilot.tripRoutes.get(name);
        if (r == null || r.legs().isEmpty()) {
            ctx.getEmbed().title("Nothing to remove").description("Route '" + name + "' has no legs.");
            return ERROR;
        }
        r.legs().remove(r.legs().size() - 1);
        ctx.getEmbed().title("Route '" + name + "' — last leg removed").description(r.legs().size() + " leg(s) left.");
        return OK;
    }

    private int delRoute(CommandContext ctx, String name) {
        boolean removed = CONFIG.client.extra.elytraPilot.tripRoutes.remove(name) != null;
        ctx.getEmbed().title(removed ? "Route '" + name + "' deleted" : "No such route '" + name + "'");
        return removed ? OK : ERROR;
    }

    private int listRoutes(CommandContext ctx) {
        var routes = CONFIG.client.extra.elytraPilot.tripRoutes;
        if (routes.isEmpty()) {
            ctx.getEmbed().title("Saved routes").description("None. Create one: `fly trip route new <name> ow <x> <y> <z>`.");
            return OK;
        }
        StringBuilder sb = new StringBuilder();
        for (Route r : routes.values())
            sb.append("**").append(r.id()).append("** — ").append(r.legs().size()).append(" leg(s), ends ")
              .append(r.endInNether() ? "nether" : "overworld " + r.destX() + "," + r.destY() + "," + r.destZ()).append('\n');
        ctx.getEmbed().title("Saved routes (" + routes.size() + ")").description(sb.toString());
        return OK;
    }

    private int showRoute(CommandContext ctx, String name) {
        Route r = CONFIG.client.extra.elytraPilot.tripRoutes.get(name);
        if (r == null) { ctx.getEmbed().title("No such route '" + name + "'"); return ERROR; }
        ctx.getEmbed().title("Route '" + r.id() + "'").description(routeSummary(r));
        return OK;
    }

    private int runRoute(CommandContext ctx, String name) {
        var cfg = CONFIG.client.extra.elytraPilot;
        Route r = cfg.tripRoutes.get(name);
        if (r == null) {
            ctx.getEmbed().title("No such route '" + name + "'").description("`fly trip route list` to see saved routes.");
            return ERROR;
        }
        if (r.legs().isEmpty()) {
            ctx.getEmbed().title("Route '" + name + "' has no legs").description("Add some with `fly trip route leg`.");
            return ERROR;
        }
        cfg.tripActiveRoute = name;
        cfg.tripActive = true;
        MODULE.get(ElytraTrip.class).syncEnabledFromConfig();
        ctx.getEmbed().title("Route '" + name + "' launched").description(routeSummary(r));
        return OK;
    }

    private String routeSummary(Route r) {
        StringBuilder sb = new StringBuilder();
        int lastX = 0, lastZ = 0;
        for (int i = 0; i < r.legs().size(); i++) {
            Route.Leg leg = r.legs().get(i);
            sb.append(i + 1).append(". ").append(leg.ride() ? "RIDE" : "FLY ").append(" → ")
              .append(leg.x()).append(", ").append(leg.z());
            if (leg.ride()) sb.append(" (y").append(leg.roadY()).append(')');
            sb.append('\n');
            lastX = leg.x(); lastZ = leg.z();
        }
        sb.append("Ends: ").append(r.endInNether()
            ? "land in the nether at " + lastX + ", " + lastZ
            : "overworld portal-out to " + r.destX() + ", " + r.destY() + ", " + r.destZ());
        return sb.toString();
    }

    private static double[] unitVec(HighwayDir d) {
        double s = 0.7071067811865476;
        return switch (d) {
            case N -> new double[]{0, -1};  case S -> new double[]{0, 1};
            case E -> new double[]{1, 0};   case W -> new double[]{-1, 0};
            case NE -> new double[]{s, -s}; case SE -> new double[]{s, s};
            case SW -> new double[]{-s, s}; case NW -> new double[]{-s, -s};
        };
    }

    private boolean isRide(com.mojang.brigadier.context.CommandContext<CommandContext> c) {
        return getString(c, "mode").equalsIgnoreCase("ride");
    }
}
