package com.aquarius.command.impl;

import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.module.impl.AutoBow;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;

/**
 * Configures {@link AutoBow} — ranged combat that draws/fires a bow or charges/fires a crossbow at hostiles in a
 * distance band, cooperating with KillAura (melee up close, bow in the gap). Used standalone or enabled by
 * {@code WhisperControl}'s {@code protect}.
 */
public class AutoBowCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("autobow")
            .category(CommandCategory.MODULE)
            .aliases("bow")
            .description("""
                Ranged combat: draw+fire a bow / charge+fire a crossbow at hostiles in a distance band. Gravity-compensated
                aim with target leading and line-of-sight gating. Leaves point-blank targets to KillAura's melee.
                """)
            .usageLines(
                "on/off",
                "range <min> <max>        (engage band in blocks; default 4..32)",
                "draw <ticks>             (bow full-draw ticks; default 20)",
                "crossbowcharge <ticks>   (crossbow charge ticks; default 25)",
                "tpssync <on/off>         (scale charge by server TPS; default on)",
                "lead <on/off>            (lead moving targets; default on)",
                "los <on/off>             (require line-of-sight to fire; default on)",
                "aimheight <0..1>         (aim point up the target's body; default 0.6)",
                "hostiles <on/off>        (fire at hostile mobs; default on)",
                "players <on/off>         (fire at players; default off)",
                "prefercrossbow <on/off>  (prefer crossbow over bow; default off)",
                "cooldown <ticks>         (wait between shots; default 10)",
                "mobile <on/off>          (strafe/kite while aiming instead of standing still; default on)",
                "dodge <on/off>           (sidestep incoming arrows; default on)"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autobow")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.autoBow.enabled = getToggle(c, "toggle");
                MODULE.get(AutoBow.class).syncEnabledFromConfig();
                c.getSource().getEmbed().title("AutoBow " + toggleStrCaps(CONFIG.client.extra.autoBow.enabled));
            }))
            .then(literal("range")
                .then(argument("min", integer(0))
                .then(argument("max", integer(1)).executes(c -> {
                    var cfg = CONFIG.client.extra.autoBow;
                    cfg.minRange = getInteger(c, "min");
                    cfg.maxRange = getInteger(c, "max");
                    c.getSource().getEmbed().title("AutoBow range = " + cfg.minRange + ".." + cfg.maxRange + "b");
                }))))
            .then(literal("draw").then(argument("ticks", integer(1)).executes(c -> {
                CONFIG.client.extra.autoBow.drawTicks = getInteger(c, "ticks");
                c.getSource().getEmbed().title("AutoBow bow draw = " + CONFIG.client.extra.autoBow.drawTicks + " ticks");
            })))
            .then(literal("crossbowcharge").then(argument("ticks", integer(1)).executes(c -> {
                CONFIG.client.extra.autoBow.crossbowChargeTicks = getInteger(c, "ticks");
                c.getSource().getEmbed().title("AutoBow crossbow charge = " + CONFIG.client.extra.autoBow.crossbowChargeTicks + " ticks");
            })))
            .then(literal("tpssync").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.autoBow.tpsSync = getToggle(c, "toggle");
                c.getSource().getEmbed().title("AutoBow TPS-sync " + toggleStrCaps(CONFIG.client.extra.autoBow.tpsSync));
            })))
            .then(literal("lead").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.autoBow.leadTargets = getToggle(c, "toggle");
                c.getSource().getEmbed().title("AutoBow lead-targets " + toggleStrCaps(CONFIG.client.extra.autoBow.leadTargets));
            })))
            .then(literal("los").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.autoBow.requireLineOfSight = getToggle(c, "toggle");
                c.getSource().getEmbed().title("AutoBow require-LOS " + toggleStrCaps(CONFIG.client.extra.autoBow.requireLineOfSight));
            })))
            .then(literal("aimheight").then(argument("fraction", doubleArg(0.0, 1.0)).executes(c -> {
                CONFIG.client.extra.autoBow.aimHeightFraction = getDouble(c, "fraction");
                c.getSource().getEmbed().title("AutoBow aim height = " + CONFIG.client.extra.autoBow.aimHeightFraction);
            })))
            .then(literal("hostiles").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.autoBow.targetHostileMobs = getToggle(c, "toggle");
                c.getSource().getEmbed().title("AutoBow target-hostiles " + toggleStrCaps(CONFIG.client.extra.autoBow.targetHostileMobs));
            })))
            .then(literal("players").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.autoBow.targetPlayers = getToggle(c, "toggle");
                c.getSource().getEmbed().title("AutoBow target-players " + toggleStrCaps(CONFIG.client.extra.autoBow.targetPlayers))
                    .description("Friends + whitelisted players are always spared.");
            })))
            .then(literal("prefercrossbow").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.autoBow.preferCrossbow = getToggle(c, "toggle");
                c.getSource().getEmbed().title("AutoBow prefer-crossbow " + toggleStrCaps(CONFIG.client.extra.autoBow.preferCrossbow));
            })))
            .then(literal("cooldown").then(argument("ticks", integer(0)).executes(c -> {
                CONFIG.client.extra.autoBow.postShotCooldownTicks = getInteger(c, "ticks");
                c.getSource().getEmbed().title("AutoBow post-shot cooldown = " + CONFIG.client.extra.autoBow.postShotCooldownTicks + " ticks");
            })))
            .then(literal("mobile").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.autoBow.mobileWhileEngaging = getToggle(c, "toggle");
                c.getSource().getEmbed().title("AutoBow mobile-while-engaging " + toggleStrCaps(CONFIG.client.extra.autoBow.mobileWhileEngaging))
                    .description("Strafe/kite while aiming (hazard-gated) instead of standing still.");
            })))
            .then(literal("dodge").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.autoBow.dodgeArrows = getToggle(c, "toggle");
                c.getSource().getEmbed().title("AutoBow dodge-arrows " + toggleStrCaps(CONFIG.client.extra.autoBow.dodgeArrows));
            })));
    }
}
