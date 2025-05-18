package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.module.impl.SessionTimeLimit;

import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static com.zenith.util.math.MathHelper.formatDuration;

public class SessionTimeLimitCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("sessionTimeLimit")
            .category(CommandCategory.MODULE)
            .description("""
            Sends an in-game warning before you are kicked for reaching the 2b2t session time limit.
            """)
            .usageLines(
                "on/off",
                "refresh",
                "ingame on/off",
                "discord on/off",
                "discord mention on/off"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("sessionTimeLimit")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.sessionTimeLimit.enabled = getToggle(c, "toggle");
                MODULE.get(SessionTimeLimit.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("Session Time Limit " + toggleStrCaps(CONFIG.client.extra.sessionTimeLimit.enabled));
                return OK;
            }))
            .then(literal("refresh").executes(c -> {
                MODULE.get(SessionTimeLimit.class).refreshNow();
                c.getSource().getEmbed()
                    .title("Session Time Limit Refreshed");
                return OK;
            }))
            .then(literal("ingame").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.sessionTimeLimit.ingameNotification = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Ingame Notification " + toggleStrCaps(CONFIG.client.extra.sessionTimeLimit.ingameNotification));
            })))
            .then(literal("discord")
                .then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.sessionTimeLimit.discordNotification = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                        .title("Discord Notification " + toggleStrCaps(CONFIG.client.extra.sessionTimeLimit.discordNotification));
                }))
                .then(literal("mention").then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.sessionTimeLimit.discordNotificationMention = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                        .title("Discord Mention " + toggleStrCaps(CONFIG.client.extra.sessionTimeLimit.discordNotificationMention));
                }))));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
            .addField("Session TIme Limit", toggleStr(CONFIG.client.extra.sessionTimeLimit.enabled))
            .addField("Limit", formatDuration(MODULE.get(SessionTimeLimit.class).getSessionTimeLimit()))
            .addField("Ingame Notification", toggleStr(CONFIG.client.extra.sessionTimeLimit.ingameNotification))
            .addField("Discord Notification", toggleStr(CONFIG.client.extra.sessionTimeLimit.discordNotification))
            .addField("Discord Mention", toggleStr(CONFIG.client.extra.sessionTimeLimit.discordNotificationMention))
            .primaryColor();
    }
}
