package com.aquarius.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.discord.DiscordBot;
import com.aquarius.discord.Embed;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.discord.DiscordBot.escape;

public class SixB6TTpaCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("sixb6ttpa")
            .category(CommandCategory.MANAGE)
            .description("""
            Manage the whitelist for 6b6t /tpa auto-accept (SixB6TTpaAutoAccept).

            Only names on this whitelist are auto-accepted with "/tpy <name>" -- 6b6t delivers
            player chat as a system message too, so an un-whitelisted name is never trusted just
            because a request-shaped line named it.
            """)
            .usageLines(
                "add <name>",
                "del <name>",
                "list"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("sixb6ttpa")
            .then(literal("add").then(argument("name", string()).executes(c -> {
                var name = getString(c, "name");
                var whitelist = CONFIG.client.extra.sixB6TTpaAutoAccept.whitelist;
                if (whitelist.stream().anyMatch(existing -> existing.equalsIgnoreCase(name))) {
                    c.getSource().getEmbed().title("Already whitelisted: " + escape(name));
                    return OK;
                }
                whitelist.add(name);
                c.getSource().getEmbed().title("Whitelisted for tpa auto-accept: " + escape(name));
                return OK;
            })))
            .then(literal("del").then(argument("name", string()).executes(c -> {
                var name = getString(c, "name");
                var removed = CONFIG.client.extra.sixB6TTpaAutoAccept.whitelist.removeIf(existing -> existing.equalsIgnoreCase(name));
                c.getSource().getEmbed().title(removed ? "Removed: " + escape(name) : "Not on the whitelist: " + escape(name));
                return OK;
            })))
            .then(literal("list").executes(c -> {
                c.getSource().getEmbed().title("6b6t tpa auto-accept whitelist");
                return OK;
            }));
    }

    @Override
    public void defaultEmbed(final Embed builder) {
        var whitelist = CONFIG.client.extra.sixB6TTpaAutoAccept.whitelist;
        var description = whitelist.isEmpty()
            ? "*(empty)*"
            : String.join(", ", whitelist.stream().map(DiscordBot::escape).toList());
        builder
            .description(description)
            .primaryColor();
    }
}
