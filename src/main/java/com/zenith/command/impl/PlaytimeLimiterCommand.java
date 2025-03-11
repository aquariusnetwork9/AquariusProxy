package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.Command;
import com.zenith.command.CommandUsage;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;
import com.zenith.discord.Embed;
import com.zenith.module.impl.PlaytimeLimiter;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Shared.CONFIG;
import static com.zenith.Shared.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class PlaytimeLimiterCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("playtimeLimiter")
            .category(CommandCategory.MODULE)
            .description("""
              Limits players from playing longer than a certain period of time.
              
              And limits players from reconnecting to play again for a certain period of time.
              """)
            .usageLines(
                "on/off",
                "sessionLength on/off",
                "sessionLength seconds <seconds>",
                "connectionInterval on/off",
                "connectionInterval seconds <seconds>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("playtimeLimiter")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.server.playtimeLimiter.enabled = getToggle(c, "toggle");
                MODULE.get(PlaytimeLimiter.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("Playtime Limiter " + toggleStrCaps(CONFIG.server.playtimeLimiter.enabled));
                return OK;
            }))
            .then(literal("sessionLength")
                      .then(argument("toggle", toggle()).executes(c -> {
                          CONFIG.server.playtimeLimiter.limitSessionLength = getToggle(c, "toggle");
                          c.getSource().getEmbed()
                              .title("Limit Session Length " + toggleStrCaps(CONFIG.server.playtimeLimiter.enabled));
                          return OK;
                      }))
                      .then(literal("seconds").then(argument("secondsArg", integer(0, 1_000_000)).executes(c -> {;
                          CONFIG.server.playtimeLimiter.maxSessionLengthSeconds = getInteger(c, "secondsArg");
                          c.getSource().getEmbed()
                              .title("Session Length Set");
                          return OK;
                      }))))
            .then(literal("connectionInterval")
                      .then(argument("toggle", toggle()).executes(c -> {
                            CONFIG.server.playtimeLimiter.limitConnectionInterval = getToggle(c, "toggle");
                            c.getSource().getEmbed()
                                .title("Limit Connection Interval " + toggleStrCaps(CONFIG.server.playtimeLimiter.enabled));
                            return OK;
                      }))
                      .then(literal("seconds").then(argument("secondsArg", integer(0, 1_000_000)).executes(c -> {
                          CONFIG.server.playtimeLimiter.connectionIntervalCooldownSeconds = getInteger(c, "secondsArg");
                          c.getSource().getEmbed()
                              .title("Connection Interval Set");
                          return OK;
                      }))));
    }

    @Override
    public void postPopulate(Embed embed) {
        embed
            .addField("Playtime Limiter", toggleStr(CONFIG.server.playtimeLimiter.enabled), false)
            .addField("Limit Session Length", toggleStr(CONFIG.server.playtimeLimiter.limitSessionLength), false)
            .addField("Max Session Length", CONFIG.server.playtimeLimiter.maxSessionLengthSeconds + " seconds", false)
            .addField("Limit Connection Interval", toggleStr(CONFIG.server.playtimeLimiter.limitConnectionInterval), false)
            .addField("Connection Interval", CONFIG.server.playtimeLimiter.connectionIntervalCooldownSeconds + " seconds", false)
            .primaryColor();
    }
}
