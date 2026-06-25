package com.aquarius.command.impl;

import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.discord.Embed;
import com.aquarius.module.impl.HighwayBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;

public class HighwayCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("highway")
            .category(CommandCategory.MODULE)
            .aliases("hwy", "highwaybuilder")
            .description("""
                Auto-build a nether highway (Meteor-style): clears the head/flight room and lays an obsidian road in
                one of the 8 directions (n/s/e/w/ne/nw/se/sw) from the bot's position, restocking the block from nearby
                chests / placed shulker boxes. Baritone handles the movement. Position the bot on the highway line first.
                """)
            .usageLines(
                "on/off",
                "start [direction]       (begin from here; uses the set direction if omitted)",
                "pause                   (soft-pause; resume with start)",
                "stop                    (stop and disarm)",
                "status",
                "dir <n/s/e/w/ne/nw/se/sw>",
                "width <blocks>          (to each side of centre; 2 => 5-wide)",
                "height <blocks>         (air clearance above the road)",
                "distance <blocks>       (0 = until stopped / world border)",
                "block <item>            (building block, default obsidian)",
                "floor <on/off>",
                "walls <on/off>          (1-high edge railings)",
                "restock chests <on/off>",
                "restock shulkers <on/off>",
                "restock radius <blocks>"
            )
            .build();
    }

    private HighwayBuilder module() {
        return MODULE.get(HighwayBuilder.class);
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("highway")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.highwayBuilder.enabled = getToggle(c, "toggle");
                module().syncEnabledFromConfig();
                c.getSource().getEmbed().title("Highway Builder " + toggleStrCaps(CONFIG.client.extra.highwayBuilder.enabled));
            }))
            .then(literal("start")
                .executes(c -> { startResult(c, null); })
                .then(argument("direction", word()).executes(c -> { startResult(c, getString(c, "direction")); })))
            .then(literal("pause").executes(c -> {
                module().pause();
                c.getSource().getEmbed().title("Highway build paused");
            }))
            .then(literal("stop").executes(c -> {
                module().stop();
                c.getSource().getEmbed().title("Highway build stopped");
            }))
            .then(literal("status").executes(c -> { statusEmbed(c.getSource().getEmbed()); }))
            .then(literal("dir").then(argument("direction", word()).executes(c -> {
                CONFIG.client.extra.highwayBuilder.direction = getString(c, "direction").toLowerCase();
                c.getSource().getEmbed().title("Highway direction = " + CONFIG.client.extra.highwayBuilder.direction);
            })))
            .then(literal("width").then(argument("blocks", integer(0, 8)).executes(c -> {
                CONFIG.client.extra.highwayBuilder.width = getInteger(c, "blocks");
                c.getSource().getEmbed().title("Highway width = " + CONFIG.client.extra.highwayBuilder.width + " (per side)");
            })))
            .then(literal("height").then(argument("blocks", integer(1, 16)).executes(c -> {
                CONFIG.client.extra.highwayBuilder.clearHeight = getInteger(c, "blocks");
                c.getSource().getEmbed().title("Highway clearance height = " + CONFIG.client.extra.highwayBuilder.clearHeight);
            })))
            .then(literal("distance").then(argument("blocks", integer(0)).executes(c -> {
                CONFIG.client.extra.highwayBuilder.targetDistance = getInteger(c, "blocks");
                int d = CONFIG.client.extra.highwayBuilder.targetDistance;
                c.getSource().getEmbed().title("Highway target distance = " + (d == 0 ? "unlimited" : d + " blocks"));
            })))
            .then(literal("block").then(argument("item", word()).executes(c -> {
                CONFIG.client.extra.highwayBuilder.block = getString(c, "item").toLowerCase();
                c.getSource().getEmbed().title("Highway building block = " + CONFIG.client.extra.highwayBuilder.block);
            })))
            .then(literal("floor").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.highwayBuilder.floor = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Highway floor " + toggleStrCaps(CONFIG.client.extra.highwayBuilder.floor));
            })))
            .then(literal("walls").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.highwayBuilder.walls = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Highway walls " + toggleStrCaps(CONFIG.client.extra.highwayBuilder.walls));
            })))
            .then(literal("restock")
                .then(literal("chests").then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.highwayBuilder.restockFromChests = getToggle(c, "toggle");
                    c.getSource().getEmbed().title("Restock from chests " + toggleStrCaps(CONFIG.client.extra.highwayBuilder.restockFromChests));
                })))
                .then(literal("shulkers").then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.highwayBuilder.restockFromShulkers = getToggle(c, "toggle");
                    c.getSource().getEmbed().title("Restock from shulkers " + toggleStrCaps(CONFIG.client.extra.highwayBuilder.restockFromShulkers));
                })))
                .then(literal("radius").then(argument("blocks", integer(1)).executes(c -> {
                    CONFIG.client.extra.highwayBuilder.restockRadius = getInteger(c, "blocks");
                    c.getSource().getEmbed().title("Restock radius = " + CONFIG.client.extra.highwayBuilder.restockRadius + " blocks");
                }))));
    }

    private void startResult(com.mojang.brigadier.context.CommandContext<CommandContext> c, String dir) {
        String err = module().start(dir);
        if (err != null) c.getSource().getEmbed().title("Can't start").addField("Error", err, false).errorColor();
        else c.getSource().getEmbed().title("Highway build started").successColor();
    }

    private void statusEmbed(Embed embed) {
        var cfg = CONFIG.client.extra.highwayBuilder;
        HighwayBuilder m = module();
        embed.title("Highway Builder").primaryColor()
            .addField("Phase", m.phase().name(), true)
            .addField("Direction", cfg.direction, true)
            .addField("Profile", "width " + cfg.width + " (" + (cfg.width * 2 + 1) + "-wide), clear " + cfg.clearHeight
                + ", floor " + toggleStr(cfg.floor) + ", walls " + toggleStr(cfg.walls), false)
            .addField("Block", cfg.block, true)
            .addField("Target", cfg.targetDistance == 0 ? "unlimited" : cfg.targetDistance + " blocks", true)
            .addField("Built", Math.round(m.distanceBuilt()) + " blocks (" + m.placedCount() + " placed, "
                + m.clearedCount() + " cleared, " + m.skippedCount() + " skipped)", false)
            .addField("Restock", "chests " + toggleStr(cfg.restockFromChests) + ", shulkers " + toggleStr(cfg.restockFromShulkers)
                + " (r=" + cfg.restockRadius + ")", false);
    }
}
