package com.zenith.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.Command;
import com.zenith.command.CommandUsage;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;
import com.zenith.discord.Embed;
import com.zenith.module.impl.ChatControl;

import static com.zenith.Shared.*;
import static com.zenith.command.brigadier.CustomStringArgumentType.getString;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static com.zenith.discord.DiscordBot.escape;

public class ChatControlCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("chatControl")
            .category(CommandCategory.MODULE)
            .description("""
            Control the proxy with whispers
            """)
            .usageLines(
                "on/off",
                "blacklist list",
                "blacklist add/del <player>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("chatControl")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.chatControl.enabled = getToggle(c, "toggle");
                MODULE.get(ChatControl.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("Chat Control " + toggleStrCaps(CONFIG.client.extra.chatControl.enabled))
                    .primaryColor();
                return OK;
            }))
            .then(literal("blacklist")
                      .then(literal("add").then(argument("player", wordWithChars()).executes(c -> {
                          final String player = StringArgumentType.getString(c, "player");
                          PLAYER_LISTS.getChatControlBlacklist().add(player)
                              .ifPresentOrElse(e ->
                                                   c.getSource().getEmbed()
                                                       .title("Added user: " + escape(e.getUsername()) + " To Blacklist"),
                                               () -> c.getSource().getEmbed()
                                                   .title("Failed to add user: " + escape(player) + " to blacklist. Unable to lookup profile."));
                          return OK;
                      })))
                      .then(literal("del").then(argument("player", wordWithChars()).executes(c -> {
                            final String player = getString(c, "player");
                            PLAYER_LISTS.getChatControlBlacklist().remove(player);
                            c.getSource().getEmbed()
                                .title("Removed user: " + escape(player) + " From Blacklist");
                            return OK;
                      })))
                      .then(literal("list").executes(c -> {
                            c.getSource().getEmbed()
                                .title("Chat Control Blacklist");
                            return OK;
                      })));
    }

    @Override
    public void postPopulate(Embed embed) {
        embed
            .addField("Chat Control", toggleStr(CONFIG.client.extra.chatControl.enabled), false)
            .description(PLAYER_LISTS.getChatControlBlacklist().entries().stream()
                             .map(e -> escape(e.getUsername()))
                             .reduce((a, b) -> a + "\n" + b)
                             .orElse("No users in blacklist"))
            .primaryColor();
    }
}
