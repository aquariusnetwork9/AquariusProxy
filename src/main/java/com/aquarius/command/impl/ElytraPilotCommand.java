package com.aquarius.command.impl;

import com.aquarius.module.impl.ElytraPilot;
import com.aquarius.util.config.Config.Client.Extra.ElytraPilot.HighwayDir;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.aquarius.Globals.CONFIG;
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
                "descend <blocks>      (distance from target to start descending)",
                "takeoff <on/off>      (on = pulse-jump to deploy; off = assume airborne/ledge)",
                "swap <on/off>         (auto-swap in a fresh elytra mid-flight when the worn one wears out)",
                "swapdur <n>           (swap the worn elytra at this remaining durability; max is 432)",
                "sparedur <n>          (min remaining durability for an inventory elytra to count as a spare)",
                "clearance <blocks>    (min height above ground before a flight-dropping swap is attempted)",
                "ebounce <on/off>      (bounce-highway mode: skip along a flat road, no fireworks)",
                "road <y>              (the flat road's surface Y, for ebounce)",
                "maxspeed <bps>        (speed cap in blocks/sec; 2b2t limit is 40 — keep ~38)",
                "highway <dir>         (follow a 2b2t nether highway from 0,0: N/S/E/W/NE/SE/NW/SW; sets ebounce + y120)",
                "pass <on/off>         (on obstacle: settle + Baritone past it along the axis, then resume bounce)",
                "passahead <blocks>    (how far along the axis to aim the Baritone bypass)"
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
            })));
    }
}
