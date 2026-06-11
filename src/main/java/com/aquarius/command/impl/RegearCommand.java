package com.aquarius.command.impl;

import com.aquarius.module.impl.Regear;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.discord.Embed;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;
import static com.aquarius.Globals.CONFIG;

public class RegearCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("regear")
            .category(CommandCategory.MODULE)
            .aliases("rg")
            .description("""
                Resupply from a kit shulker in an ender chest: place/open the echest, pull the named
                kit shulker, empty it, return it, then gear up. One-shot. Short alias: .rg
                """)
            .usageLines(
                "on/off",
                "name <text>  (custom name of the kit shulker)",
                "color <name>/off  (match the kit shulker by colour instead)",
                "scanradius <n>  (fallback: find a placed echest within n blocks)",
                "armor on/off  (equip the kit's armor on finish)",
                "totem on/off  (put a totem in the offhand on finish)",
                "return on/off  (return the emptied shulker to the echest)",
                "pauseplayer on/off",
                "once on/off  (toggle the module off after a successful regear)"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("regear")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.enabled = getToggle(c, "toggle");
                MODULE.get(Regear.class).syncEnabledFromConfig();
                c.getSource().getEmbed().title("Regear " + toggleStrCaps(CONFIG.client.extra.regear.enabled));
            }))
            .then(literal("name").then(argument("text", word()).executes(c -> {
                CONFIG.client.extra.regear.kitShulkerName = getString(c, "text");
                CONFIG.client.extra.regear.matchByColor = false;
                c.getSource().getEmbed().title("Kit shulker name: " + getString(c, "text"))
                    .description("Matches a shulker whose custom name contains this (case-insensitive).");
            })))
            .then(literal("color")
                .then(literal("off").executes(c -> {
                    CONFIG.client.extra.regear.matchByColor = false;
                    c.getSource().getEmbed().title("Kit shulker matched by name");
                }))
                .then(argument("name", word()).executes(c -> {
                    CONFIG.client.extra.regear.kitShulkerColor = getString(c, "name").toLowerCase();
                    CONFIG.client.extra.regear.matchByColor = true;
                    c.getSource().getEmbed().title("Kit shulker color: " + getString(c, "name"))
                        .description("Matches a " + getString(c, "name") + "_shulker_box.");
                })))
            .then(literal("scanradius").then(argument("n", integer(1)).executes(c -> {
                CONFIG.client.extra.regear.echestScanRadius = getInteger(c, "n");
                c.getSource().getEmbed().title("Echest fallback scan radius: " + getInteger(c, "n") + " blocks");
            })))
            .then(literal("armor").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.equipArmor = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Equip armor " + toggleStrCaps(CONFIG.client.extra.regear.equipArmor));
            })))
            .then(literal("totem").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.offhandTotem = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Offhand totem " + toggleStrCaps(CONFIG.client.extra.regear.offhandTotem));
            })))
            .then(literal("return").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.returnShulker = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Return emptied shulker " + toggleStrCaps(CONFIG.client.extra.regear.returnShulker));
            })))
            .then(literal("pauseplayer").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.pauseOnPlayer = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Pause on player " + toggleStrCaps(CONFIG.client.extra.regear.pauseOnPlayer));
            })))
            .then(literal("once").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.disableWhenDone = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Disable-when-done " + toggleStrCaps(CONFIG.client.extra.regear.disableWhenDone));
            })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        var cfg = CONFIG.client.extra.regear;
        var module = MODULE.get(Regear.class);
        embed
            .primaryColor()
            .addField("Enabled", toggleStr(cfg.enabled))
            .addField("State", module.statusLine())
            .addField("Kit shulker", cfg.matchByColor ? "color: " + cfg.kitShulkerColor : "name: " + cfg.kitShulkerName)
            .addField("Echest", "place own (fallback scan " + cfg.echestScanRadius + "b)")
            .addField("Gear up", "armor " + toggleStr(cfg.equipArmor) + ", totem " + toggleStr(cfg.offhandTotem))
            .addField("Return shulker", toggleStr(cfg.returnShulker))
            .addField("Safety", "player-pause " + toggleStr(cfg.pauseOnPlayer) + " (" + (int) cfg.playerPauseRange + "b)")
            .addField("One-shot", toggleStr(cfg.disableWhenDone));
    }
}
