package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.zenith.Globals.CONFIG;
import static com.zenith.command.brigadier.CustomStringArgumentType.getString;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class ExtraChatCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("extraChat")
            .category(CommandCategory.MODULE)
            .description("""
                 Hide certain types of messages in-game or in the terminal chat log.
                 """)
            .usageLines(
                "on/off",
                "hideChat on/off",
                "hideWhispers on/off",
                "hideDeathMessages on/off",
                "showConnectionMessages on/off",
                "logChatMessages on/off",
                "logOnlyQueuePositionUpdates on/off",
                "whisperCommand <command>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("extraChat")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.chat.enabled = getToggle(c, "toggle");
                c.getSource().getEmbed().title("ExtraChat " + toggleStrCaps(CONFIG.client.extra.chat.enabled));
                return OK;
            }))
            .then(literal("hideChat").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.chat.hideChat = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Hide Chat " + toggleStrCaps(CONFIG.client.extra.chat.hideChat));
                return OK;
            })))
            .then(literal("hideWhispers").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.chat.hideWhispers = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Hide Whispers " + toggleStrCaps(CONFIG.client.extra.chat.hideWhispers));
                return OK;
            })))
            .then(literal("hideDeathMessages").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.chat.hideDeathMessages = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Hide Death Messages " + toggleStrCaps(CONFIG.client.extra.chat.hideDeathMessages));
                return OK;
            })))
            .then(literal("showConnectionMessages").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.chat.showConnectionMessages = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Show Connection Messages " + toggleStrCaps(CONFIG.client.extra.chat.showConnectionMessages));
                return OK;
            })))
            .then(literal("logChatMessages").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.logChatMessages = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Log Chat Messages " + toggleStrCaps(CONFIG.client.extra.logChatMessages));
                return OK;
            })))
            .then(literal("logOnlyQueuePositionUpdates").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.logOnlyQueuePositionUpdates = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Log Only Queue Pos Updates " + toggleStrCaps(CONFIG.client.extra.logOnlyQueuePositionUpdates));
                return OK;
            })))
            .then(literal("whisperCommand").then(argument("cmd", wordWithChars()).executes(c -> {
                String cmd = getString(c, "cmd");
                if (cmd.isBlank()) {
                    c.getSource().getEmbed()
                        .title("Invalid Whisper Command");
                    return ERROR;
                }
                CONFIG.client.extra.whisperCommand = cmd;
                c.getSource().getEmbed()
                    .title("Whisper Command Set");
                return OK;
            })));
    }


    @Override
    public void defaultEmbed(final Embed builder) {
        builder
            .addField("ExtraChat", toggleStr(CONFIG.client.extra.chat.enabled), false)
            .addField("Hide Chat", toggleStr(CONFIG.client.extra.chat.hideChat), false)
            .addField("Hide Whispers", toggleStr(CONFIG.client.extra.chat.hideWhispers), false)
            .addField("Hide death Messages", toggleStr(CONFIG.client.extra.chat.hideDeathMessages), false)
            .addField("Show Connection Messages", toggleStr(CONFIG.client.extra.chat.showConnectionMessages), false)
            .addField("Log Chat Messages", toggleStr(CONFIG.client.extra.logChatMessages), false)
            .addField("Log Only Queue Pos Updates", toggleStr(CONFIG.client.extra.logOnlyQueuePositionUpdates), false)
            .addField("Whisper Command", CONFIG.client.extra.whisperCommand, false)
            .primaryColor();
    }
}
