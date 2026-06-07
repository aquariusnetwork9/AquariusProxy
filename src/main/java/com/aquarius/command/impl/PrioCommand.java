package com.aquarius.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.discord.Embed;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;

public class PrioCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("prio")
            .category(CommandCategory.INFO)
            .description("Configure alerts for 2b2t priority queue status")
            .usageLines(
                "mentions on/off"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("prio")
            .then(literal("mentions")
                      .then(argument("toggle", toggle()).executes(c -> {
                            CONFIG.discord.mentionRoleOnPrioUpdate = getToggle(c, "toggle");
                            c.getSource().getEmbed()
                                .title("Prio Mentions " + toggleStrCaps(CONFIG.discord.mentionRoleOnPrioUpdate));
                            return OK;
                        })));
    }

    @Override
    public void defaultEmbed(Embed builder) {
        builder
            .addField("Prio Status Mentions", toggleStr(CONFIG.discord.mentionRoleOnPrioUpdate), true)
            .primaryColor();
    }
}
