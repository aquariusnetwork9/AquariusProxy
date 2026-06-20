package com.aquarius.command.impl;

import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.module.impl.WhisperControl;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.DISCORD;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;

/**
 * Configures {@link WhisperControl} — the handler that lets authorized players whisper the bot verbs
 * (follow / come / patrol / mine / stop). Sets the toggles and the patrol/mine presets, and posts the Discord panel.
 */
public class WhisperControlCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("whispercontrol")
            .category(CommandCategory.MODULE)
            .aliases("wc")
            .description("""
                Let authorized players whisper the bot commands: follow, come, patrol, mine, stop.
                Authorization is RBAC when enabled (module.whispercontrol + the verb's perm), else owner-only.
                """)
            .usageLines(
                "on/off",
                "followradius <blocks>          (leash radius for `follow`; default 32)",
                "comethreshold <blocks>         (`come` walks within this, flies beyond it; default 200)",
                "killaura <on/off>              (`follow` also turns on KillAura)",
                "patrol <x> <y> <z> <range>     (set the `patrol` preset area)",
                "mine <x1> <z1> <x2> <z2> <minY> <maxY>  (set the `mine` preset box)",
                "panel                          (post the interactive control panel to Discord)"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("whispercontrol")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.whisperControl.enabled = getToggle(c, "toggle");
                MODULE.get(WhisperControl.class).syncEnabledFromConfig();
                c.getSource().getEmbed().title("Whisper control " + toggleStrCaps(CONFIG.client.extra.whisperControl.enabled));
            }))
            .then(literal("followradius").then(argument("blocks", integer(1)).executes(c -> {
                CONFIG.client.extra.whisperControl.followRadius = getInteger(c, "blocks");
                c.getSource().getEmbed().title("Whisper control follow radius = " + CONFIG.client.extra.whisperControl.followRadius + "b");
            })))
            .then(literal("comethreshold").then(argument("blocks", integer(0)).executes(c -> {
                CONFIG.client.extra.whisperControl.comeFlyThreshold = getInteger(c, "blocks");
                c.getSource().getEmbed().title("Whisper control come fly-threshold = " + CONFIG.client.extra.whisperControl.comeFlyThreshold + "b")
                    .description("`come` walks when you're within this, flies (ElytraTrip) beyond it.");
            })))
            .then(literal("killaura").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.whisperControl.followEnablesKillAura = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Whisper control follow-killaura " + toggleStrCaps(CONFIG.client.extra.whisperControl.followEnablesKillAura));
            })))
            .then(literal("patrol")
                .then(argument("x", integer())
                .then(argument("y", integer())
                .then(argument("z", integer())
                .then(argument("range", integer(1)).executes(c -> {
                    var wc = CONFIG.client.extra.whisperControl;
                    wc.patrolCenterX = getInteger(c, "x");
                    wc.patrolCenterY = getInteger(c, "y");
                    wc.patrolCenterZ = getInteger(c, "z");
                    wc.patrolRange = getInteger(c, "range");
                    wc.patrolConfigured = true;
                    c.getSource().getEmbed().title("Whisper control patrol preset set")
                        .description("Center " + wc.patrolCenterX + ", " + wc.patrolCenterY + ", " + wc.patrolCenterZ + " — range " + wc.patrolRange);
                }))))))
            .then(literal("mine")
                .then(argument("x1", integer())
                .then(argument("z1", integer())
                .then(argument("x2", integer())
                .then(argument("z2", integer())
                .then(argument("minY", integer())
                .then(argument("maxY", integer()).executes(c -> {
                    var wc = CONFIG.client.extra.whisperControl;
                    wc.mineCorner1X = getInteger(c, "x1"); wc.mineCorner1Z = getInteger(c, "z1");
                    wc.mineCorner2X = getInteger(c, "x2"); wc.mineCorner2Z = getInteger(c, "z2");
                    wc.mineMinY = getInteger(c, "minY"); wc.mineMaxY = getInteger(c, "maxY");
                    wc.mineConfigured = true;
                    c.getSource().getEmbed().title("Whisper control mine preset set")
                        .description("Box " + wc.mineCorner1X + "," + wc.mineCorner1Z + " -> " + wc.mineCorner2X + "," + wc.mineCorner2Z
                            + " (y " + wc.mineMinY + ".." + wc.mineMaxY + ")");
                }))))))))
            .then(literal("panel").executes(c -> {
                boolean posted = DISCORD.openWhisperControlPanel();
                c.getSource().getEmbed()
                    .title(posted ? "Whisper control panel posted to Discord" : "Discord bot not running")
                    .description(posted ? "Toggle the handler and set the patrol / mine presets from the panel." : "Enable the Discord bot to use the panel.");
            }));
    }
}
