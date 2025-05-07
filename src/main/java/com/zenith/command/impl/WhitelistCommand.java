package com.zenith.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.PLAYER_LISTS;
import static com.zenith.command.api.CommandOutputHelper.playerListToString;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static com.zenith.discord.DiscordBot.escape;

public class WhitelistCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("whitelist")
            .category(CommandCategory.CORE)
            .description("""
            Manages the list of players allowed to login.
            
            Whitelisted players are allowed to both control the account in-game and spectate.
            
            `autoAddZenithAccount` will add the MC account you have logged in Zenith with to the whitelist.
            
            Blacklist is only used and shown if the whitelist or spectator whitelist is disabled (see the `unsupported` command`)
            """)
            .usageLines(
                "add/del <player>",
                "list",
                "clear",
                "autoAddZenithAccount on/off",
                "blacklist add/del <player>",
                "blacklist clear"
            )
            .aliases("wl")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("whitelist")
                .then(literal("add").requires(Command::validateAccountOwner).then(argument("player", string()).executes(c -> {
                    final String player = StringArgumentType.getString(c, "player");
                    PLAYER_LISTS.getWhitelist().add(player).ifPresentOrElse(e ->
                                    c.getSource().getEmbed()
                                            .title("Added user: " + escape(e.getUsername()) + " To Whitelist"),
                                                                            () -> c.getSource().getEmbed()
                                    .title("Failed to add user: " + escape(player) + " to whitelist. Unable to lookup profile."));
                    return OK;
                })))
                .then(literal("del").requires(Command::validateAccountOwner).then(argument("player", string()).executes(c -> {
                    final String player = StringArgumentType.getString(c, "player");
                    PLAYER_LISTS.getWhitelist().remove(player);
                    c.getSource().getEmbed()
                            .title("Removed user: " + escape(player) + " From Whitelist");
                    Proxy.getInstance().kickNonWhitelistedPlayers();
                    return OK;
                })))
                .then(literal("list").executes(c -> {
                    c.getSource().getEmbed()
                            .title("Whitelist List");
                }))
                .then(literal("clear").requires(Command::validateAccountOwner).executes(c -> {
                    PLAYER_LISTS.getWhitelist().clear();
                    c.getSource().getEmbed()
                            .title("Whitelist Cleared");
                    Proxy.getInstance().kickNonWhitelistedPlayers();
                    return OK;
                }))
            .then(literal("autoAddZenithAccount").requires(Command::validateAccountOwner)
                      .then(argument("toggle", toggle()).executes(c -> {
                          CONFIG.server.extra.whitelist.autoAddClient = getToggle(c, "toggle");
                          c.getSource().getEmbed()
                              .title("Auto Add Zenith Account " + toggleStrCaps(CONFIG.server.extra.whitelist.autoAddClient));
                          return OK;
                      })))
            .then(literal("blacklist").requires(Command::validateAccountOwner)
                      .then(literal("add").then(argument("player", string()).executes(c -> {
                          final String player = StringArgumentType.getString(c, "player");
                          PLAYER_LISTS.getBlacklist().add(player).ifPresentOrElse(e ->
                              c.getSource().getEmbed()
                                  .title("Added user: " + escape(e.getUsername()) + " To Blacklist"),
                              () -> c.getSource().getEmbed()
                                  .title("Failed to add user: " + escape(player) + " to blacklist. Unable to lookup profile."));
                          return OK;
                      })))
                      .then(literal("del").then(argument("player", string()).executes(c -> {
                          final String player = StringArgumentType.getString(c, "player");
                          PLAYER_LISTS.getBlacklist().remove(player);
                          c.getSource().getEmbed()
                              .title("Removed user: " + escape(player) + " From Blacklist");
                          Proxy.getInstance().kickNonWhitelistedPlayers();
                          return 1;
                      })))
                      .then(literal("clear").executes(c -> {
                          PLAYER_LISTS.getBlacklist().clear();
                          c.getSource().getEmbed()
                              .title("Blacklist Cleared");
                          Proxy.getInstance().kickNonWhitelistedPlayers();
                          return OK;
                      })));
    }

    @Override
    public void defaultEmbed(final Embed builder) {
        var listStr = "**Whitelist**\n" + playerListToString(PLAYER_LISTS.getWhitelist());
        if (!CONFIG.server.extra.whitelist.enable || !CONFIG.server.spectator.whitelistEnabled) {
            listStr += "\n**BlackList:**\n" + playerListToString(PLAYER_LISTS.getBlacklist());
        }
        builder
            .description(listStr)
            .addField("Auto Add Zenith Account", toggleStr(CONFIG.server.extra.whitelist.autoAddClient), false)
            .primaryColor();
    }
}
