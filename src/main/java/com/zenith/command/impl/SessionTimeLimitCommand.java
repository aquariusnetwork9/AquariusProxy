package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.Command;
import com.zenith.command.CommandUsage;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;
import com.zenith.discord.Embed;
import com.zenith.module.impl.SessionTimeLimit;

import static com.zenith.Shared.CONFIG;
import static com.zenith.Shared.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static com.zenith.util.math.MathHelper.formatDuration;
import static java.util.Arrays.asList;

public class SessionTimeLimitCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.args(
            "sessionTimeLimit",
            CommandCategory.MODULE,
            """
            Sends an in-game warning before you are kicked for reaching the 2b2t session time limit.
            """,
            asList(
                "on/off",
                "refresh"
            )
        );
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
            }));
    }

    @Override
    public void postPopulate(Embed embed) {
        embed
            .addField("Limit", formatDuration(MODULE.get(SessionTimeLimit.class).getSessionTimeLimit()), false)
            .primaryColor();
    }
}
