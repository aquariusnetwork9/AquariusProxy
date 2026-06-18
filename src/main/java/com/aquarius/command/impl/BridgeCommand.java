package com.aquarius.command.impl;

import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.discord.Embed;
import com.aquarius.feature.bridge.BridgeWaypoint;
import com.aquarius.module.impl.Bridge;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.List;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;

public class BridgeCommand extends Command {

    private static final String TEST_GROUP = "bridge.test";

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("bridge")
            .category(CommandCategory.MODULE)
            .aliases("proxybridge", "wpbridge")
            .description("""
                Bidirectional link to the ProxyBridge client mod (a Meteor addon, MC 1.21.4) over the
                proxybridge:main plugin channel. Pushes live waypoints (e.g. PearlDrop's empty stasis chambers)
                for the mod to render, and routes allow-listed commands the mod sends back.
                """)
            .usageLines(
                "on/off",
                "status                  (connected mods, published groups, settings)",
                "test                    (push a debug waypoint at the bot to verify rendering)",
                "test clear              (remove the debug waypoint)",
                "pearldrop <on/off>      (auto-publish PearlDrop empties as waypoints)",
                "spectators <on/off>     (also send to spectators, not just the controlling player)",
                "strip <on/off>          (hide proxybridge:main from the real server)",
                "interval <ticks>        (min ticks between waypoint publishes)",
                "allow list              (show the cmd/invoke allow-list)",
                "allow add <command>     (allow the mod to invoke a command)",
                "allow remove <command>  (disallow a command)"
            )
            .build();
    }

    private Bridge module() {
        return MODULE.get(Bridge.class);
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("bridge")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.bridge.enabled = getToggle(c, "toggle");
                module().syncEnabledFromConfig();
                c.getSource().getEmbed().title("Bridge " + toggleStrCaps(CONFIG.client.extra.bridge.enabled));
            }))
            .then(literal("status").executes(c -> {
                var cfg = CONFIG.client.extra.bridge;
                c.getSource().getEmbed()
                    .title("ProxyBridge status")
                    .addField("Enabled", toggleStr(cfg.enabled), true)
                    .addField("Connected mods", String.valueOf(module().modConnectionCount()), true)
                    .addField("Publish interval", cfg.publishIntervalTicks + " ticks", true)
                    .addField("Auto PearlDrop", toggleStr(cfg.autoPublishPearlDrop), true)
                    .addField("Spectators", toggleStr(cfg.broadcastToSpectators), true)
                    .addField("Strip register", toggleStr(cfg.stripRegisterChannel), true)
                    .addField("Groups", String.join(", ", module().registeredGroups()), false)
                    .addField("Invoke allow-list", String.join(", ", cfg.invokeAllowList), false)
                    .primaryColor();
            }))
            .then(literal("test")
                .executes(c -> {
                    int x = (int) Math.floor(CACHE.getPlayerCache().getX());
                    int y = (int) Math.floor(CACHE.getPlayerCache().getY());
                    int z = (int) Math.floor(CACHE.getPlayerCache().getZ());
                    var dim = CACHE.getChunkCache().getCurrentDimension();
                    String dimName = dim != null ? dim.name() : "minecraft:overworld";
                    module().publishGroup(TEST_GROUP, List.of(
                        new BridgeWaypoint("bridge_test", "Bridge Test", dimName, x, y + 3, z,
                            BridgeWaypoint.COLOR_AQUA, 0)));
                    c.getSource().getEmbed()
                        .title("Bridge test waypoint pushed")
                        .description("At " + x + ", " + (y + 3) + ", " + z + " — visible if a ProxyBridge client is connected.")
                        .successColor();
                })
                .then(literal("clear").executes(c -> {
                    module().publishGroup(TEST_GROUP, List.of());
                    c.getSource().getEmbed().title("Bridge test waypoint cleared");
                })))
            .then(literal("pearldrop").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.bridge.autoPublishPearlDrop = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Bridge auto-publish PearlDrop "
                    + toggleStrCaps(CONFIG.client.extra.bridge.autoPublishPearlDrop));
            })))
            .then(literal("spectators").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.bridge.broadcastToSpectators = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Bridge broadcast-to-spectators "
                    + toggleStrCaps(CONFIG.client.extra.bridge.broadcastToSpectators));
            })))
            .then(literal("strip").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.bridge.stripRegisterChannel = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Bridge strip-register-channel "
                    + toggleStrCaps(CONFIG.client.extra.bridge.stripRegisterChannel));
            })))
            .then(literal("interval").then(argument("ticks", integer(1)).executes(c -> {
                CONFIG.client.extra.bridge.publishIntervalTicks = getInteger(c, "ticks");
                c.getSource().getEmbed().title("Bridge publish interval = "
                    + CONFIG.client.extra.bridge.publishIntervalTicks + " ticks");
            })))
            .then(literal("allow")
                .then(literal("list").executes(c -> {
                    c.getSource().getEmbed()
                        .title("Bridge invoke allow-list")
                        .description(String.join(", ", CONFIG.client.extra.bridge.invokeAllowList))
                        .primaryColor();
                }))
                .then(literal("add").then(argument("command", word()).executes(c -> {
                    String cmd = getString(c, "command").toLowerCase();
                    var list = CONFIG.client.extra.bridge.invokeAllowList;
                    if (!list.contains(cmd)) list.add(cmd);
                    c.getSource().getEmbed().title("Bridge allow-list += " + cmd)
                        .description(String.join(", ", list));
                })))
                .then(literal("remove").then(argument("command", word()).executes(c -> {
                    String cmd = getString(c, "command").toLowerCase();
                    var list = CONFIG.client.extra.bridge.invokeAllowList;
                    list.removeIf(s -> s.equalsIgnoreCase(cmd));
                    c.getSource().getEmbed().title("Bridge allow-list -= " + cmd)
                        .description(String.join(", ", list));
                }))));
    }

    @Override
    public void defaultEmbed(final Embed embedBuilder) {
        embedBuilder
            .addField("Bridge", toggleStr(CONFIG.client.extra.bridge.enabled), true)
            .addField("Connected mods", String.valueOf(module().modConnectionCount()), true)
            .primaryColor();
    }
}
