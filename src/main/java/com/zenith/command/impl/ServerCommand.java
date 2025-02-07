package com.zenith.command.impl;

import com.google.common.primitives.Ints;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.Command;
import com.zenith.command.CommandUsage;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;
import com.zenith.discord.Embed;

import java.util.regex.Pattern;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Shared.CONFIG;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static java.util.Arrays.asList;

public class ServerCommand extends Command {
    private final Pattern ipWithPortPattern = Pattern.compile("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}:[0-9]{1,5}$");
    private final Pattern domainWithPortPattern = Pattern.compile("^[a-zA-Z0-9]+:[0-9]{1,5}$");
    private final Pattern ipv6Pattern = Pattern.compile("^((([0-9A-Fa-f]{1,4}:){1,6}:)|(([0-9A-Fa-f]{1,4}:){7}))([0-9A-Fa-f]{1,4})$");
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.args(
            "server",
            CommandCategory.MANAGE,
            "Change the MC server ZenithProxy connects to.",
            asList(
                "<IP>",
                "<IP> <port>"
            )
        );
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("server").requires(Command::validateAccountOwner)
            .then(argument("ip", wordWithChars())
                      .then(argument("port", integer(1, 65535)).executes(c -> {
                          final String ip = StringArgumentType.getString(c, "ip");
                          final int port = IntegerArgumentType.getInteger(c, "port");
                          CONFIG.client.server.address = ip;
                          CONFIG.client.server.port = port;
                          c.getSource().getEmbed()
                              .title("Server Updated!");
                          return OK;
                      }))
                      .executes(c -> {
                          final String ip = StringArgumentType.getString(c, "ip");
                          if (ipWithPortPattern.matcher(ip).matches()) {
                              final String[] split = ip.split(":");
                              if (split.length != 2) {
                                  c.getSource().getEmbed()
                                      .title("Error")
                                      .description("Invalid IP format.");
                                  return OK;
                              }
                              String ipExtracted = split[0];
                              Integer p = Ints.tryParse(split[1]);
                              if (p == null) {
                                  c.getSource().getEmbed()
                                      .title("Error")
                                      .description("Invalid IP format.");
                                  return OK;
                              }
                              CONFIG.client.server.address = ipExtracted;
                              CONFIG.client.server.port = p;
                                c.getSource().getEmbed()
                                    .title("Server Updated!");
                              return OK;
                          } else if (domainWithPortPattern.matcher(ip).matches()) {
                              final String[] split = ip.split(":");
                              if (split.length != 2) {
                                  c.getSource().getEmbed()
                                      .title("Error")
                                      .description("Invalid IP format.");
                                  return OK;
                              }
                              String ipExtracted = split[0];
                              Integer p = Ints.tryParse(split[1]);
                              if (p == null) {
                                  c.getSource().getEmbed()
                                      .title("Error")
                                      .description("Invalid IP format.");
                                  return OK;
                              }
                              CONFIG.client.server.address = ipExtracted;
                              CONFIG.client.server.port = p;
                              c.getSource().getEmbed()
                                  .title("Server Updated!");
                              return OK;
                          } else if (ipv6Pattern.matcher(ip).matches()) {
                              CONFIG.client.server.address = ip;
                              CONFIG.client.server.port = 25565;
                              c.getSource().getEmbed()
                                  .title("Server Updated!");
                              return OK;
                          } else {
                              if (ip.startsWith("<")) {
                                  c.getSource().getEmbed()
                                      .title("Error")
                                      .description("Invalid IP format.");
                                  return OK;
                              }
                              CONFIG.client.server.address = ip;
                              CONFIG.client.server.port = 25565;
                              c.getSource().getEmbed()
                                  .title("Server Updated!");
                              return OK;
                          }
                      }));
    }

    @Override
    public void postPopulate(final Embed builder) {
        builder
            .addField("IP", CONFIG.client.server.address, false)
            .addField("Port", CONFIG.client.server.port, true)
            .primaryColor();
    }
}
