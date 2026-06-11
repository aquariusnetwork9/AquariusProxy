package com.aquarius.command.impl;

import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.discord.Embed;
import com.aquarius.module.impl.OrderFiller;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;

public class OrderFillerCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("orderfiller")
            .category(CommandCategory.MODULE)
            .aliases("of")
            .description("""
                Payment-gated order picker: pop a paid job, withdraw the reserved kit shulkers and deposit them
                into the order's outgoing chest, then post a manifest. Never breaks/places. Alias: .of
                """)
            .usageLines(
                "on/off",
                "pause / resume",
                "maxpertrip <n>",
                "partial on/off  (allow partial fills)",
                "manifestchannel <id>  (Discord channel for the manifest; empty = main)",
                "test <order_id>  (manually dispatch a paid order without Redis)",
                "status"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("orderfiller")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.orderFiller.enabled = getToggle(c, "toggle");
                MODULE.get(OrderFiller.class).syncEnabledFromConfig();
                c.getSource().getEmbed().title("Order Filler " + toggleStrCaps(CONFIG.client.extra.orderFiller.enabled));
            }))
            .then(literal("pause").executes(c -> {
                MODULE.get(OrderFiller.class).pause();
                c.getSource().getEmbed().title("Order Filler paused");
            }))
            .then(literal("resume").executes(c -> {
                MODULE.get(OrderFiller.class).resume();
                c.getSource().getEmbed().title("Order Filler resumed");
            }))
            .then(literal("maxpertrip").then(argument("n", integer(1)).executes(c -> {
                CONFIG.client.extra.orderFiller.maxShulkersPerTrip = getInteger(c, "n");
                c.getSource().getEmbed().title("Max shulkers per trip: " + getInteger(c, "n"));
            })))
            .then(literal("partial").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.orderFiller.allowPartial = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Allow partial fills " + toggleStrCaps(CONFIG.client.extra.orderFiller.allowPartial));
            })))
            .then(literal("manifestchannel").then(argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                CONFIG.client.extra.orderFiller.manifestChannelId = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "id");
                c.getSource().getEmbed().title("Manifest channel set");
            })))
            .then(literal("test").then(argument("order_id", integer(1)).executes(c -> {
                int id = getInteger(c, "order_id");
                if (!CONFIG.client.extra.orderFiller.enabled) { CONFIG.client.extra.orderFiller.enabled = true; MODULE.get(OrderFiller.class).syncEnabledFromConfig(); }
                MODULE.get(OrderFiller.class).test(id);
                c.getSource().getEmbed().title("Dispatched order " + id);
            })))
            .then(literal("status").executes(c -> {
                c.getSource().getEmbed().title("Order Filler status")
                    .description(MODULE.get(OrderFiller.class).statusLine());
            }));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        var cfg = CONFIG.client.extra.orderFiller;
        embed.primaryColor()
            .addField("Enabled", toggleStr(cfg.enabled))
            .addField("State", MODULE.get(OrderFiller.class).statusLine())
            .addField("Max/trip", String.valueOf(cfg.maxShulkersPerTrip))
            .addField("Partial fills", toggleStr(cfg.allowPartial))
            .addField("Safety", "player-pause " + toggleStr(cfg.pauseOnPlayer) + " (" + (int) cfg.playerPauseRange + "b)");
    }
}
