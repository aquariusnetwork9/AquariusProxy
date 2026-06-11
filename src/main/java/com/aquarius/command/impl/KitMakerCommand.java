package com.aquarius.command.impl;

import com.aquarius.module.impl.KitMaker;
import com.aquarius.util.config.Config.Client.Extra.KitMaker.MatchMode;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.discord.Embed;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;
import static com.aquarius.Globals.CONFIG;

public class KitMakerCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("kitmaker")
            .category(CommandCategory.MODULE)
            .aliases("km")
            .description("""
                Mass-produce kit shulkers from a template chest + a floor-level chest layout. Reads the
                example kit, auto-classifies nearby ground containers, then fills + deposits kits. Alias: .km
                """)
            .usageLines(
                "on/off",
                "template <x> <y> <z>  (chest holding the example kit shulker)",
                "scanradius <n>  (container discovery radius, default 20)",
                "floorband <down> <up>  (accept containers feetY-down .. feetY+up; under-floor excluded)",
                "match loose/smart/exact",
                "enchantlevels on/off  (smart match: ignore enchant levels)",
                "maxkits <n>  (0 = until shulkers/materials run out)",
                "pauseplayer on/off",
                "autodc on/off  (disconnect when done)",
                "status  (print the discovered layout)"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("kitmaker")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.kitMaker.enabled = getToggle(c, "toggle");
                MODULE.get(KitMaker.class).syncEnabledFromConfig();
                c.getSource().getEmbed().title("Kit Maker " + toggleStrCaps(CONFIG.client.extra.kitMaker.enabled));
            }))
            .then(literal("template")
                .then(argument("x", integer()).then(argument("y", integer()).then(argument("z", integer()).executes(c -> {
                    CONFIG.client.extra.kitMaker.templateChestX = getInteger(c, "x");
                    CONFIG.client.extra.kitMaker.templateChestY = getInteger(c, "y");
                    CONFIG.client.extra.kitMaker.templateChestZ = getInteger(c, "z");
                    c.getSource().getEmbed().title("Template chest set")
                        .description("(" + getInteger(c, "x") + ", " + getInteger(c, "y") + ", " + getInteger(c, "z") + ")");
                })))))
            .then(literal("scanradius").then(argument("n", integer(1)).executes(c -> {
                CONFIG.client.extra.kitMaker.scanRadius = getInteger(c, "n");
                c.getSource().getEmbed().title("Scan radius: " + getInteger(c, "n") + " blocks");
            })))
            .then(literal("floorband")
                .then(argument("down", integer(0)).then(argument("up", integer(0)).executes(c -> {
                    CONFIG.client.extra.kitMaker.floorBandDown = getInteger(c, "down");
                    CONFIG.client.extra.kitMaker.floorBandUp = getInteger(c, "up");
                    c.getSource().getEmbed().title("Floor band: feetY -" + getInteger(c, "down") + " .. +" + getInteger(c, "up"))
                        .description("Containers below this band (under the floor) are not counted.");
                }))))
            .then(literal("match")
                .then(literal("loose").executes(c -> { CONFIG.client.extra.kitMaker.matchMode = MatchMode.Loose;
                    c.getSource().getEmbed().title("Match mode: Loose (same item type only)"); }))
                .then(literal("smart").executes(c -> { CONFIG.client.extra.kitMaker.matchMode = MatchMode.Smart;
                    c.getSource().getEmbed().title("Match mode: Smart (ignore cosmetic name/lore/durability)"); }))
                .then(literal("exact").executes(c -> { CONFIG.client.extra.kitMaker.matchMode = MatchMode.Exact;
                    c.getSource().getEmbed().title("Match mode: Exact (all components must match)"); })))
            .then(literal("enchantlevels").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.kitMaker.ignoreEnchantLevels = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Ignore enchant levels " + toggleStrCaps(CONFIG.client.extra.kitMaker.ignoreEnchantLevels));
            })))
            .then(literal("maxkits").then(argument("n", integer(0)).executes(c -> {
                CONFIG.client.extra.kitMaker.maxKits = getInteger(c, "n");
                c.getSource().getEmbed().title("Max kits: " + (getInteger(c, "n") == 0 ? "unlimited" : getInteger(c, "n")));
            })))
            .then(literal("pauseplayer").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.kitMaker.pauseOnPlayer = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Pause on player " + toggleStrCaps(CONFIG.client.extra.kitMaker.pauseOnPlayer));
            })))
            .then(literal("autodc").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.kitMaker.autoDisconnect = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Auto-disconnect when done " + toggleStrCaps(CONFIG.client.extra.kitMaker.autoDisconnect));
            })))
            .then(literal("status").executes(c -> {
                c.getSource().getEmbed().title("Kit Maker status")
                    .description(MODULE.get(KitMaker.class).statusLine() + "\n" + MODULE.get(KitMaker.class).setupLine());
            }));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        var cfg = CONFIG.client.extra.kitMaker;
        var module = MODULE.get(KitMaker.class);
        embed
            .primaryColor()
            .addField("Enabled", toggleStr(cfg.enabled))
            .addField("State", module.statusLine())
            .addField("Template chest", "(" + cfg.templateChestX + ", " + cfg.templateChestY + ", " + cfg.templateChestZ + ")")
            .addField("Scan", "radius " + cfg.scanRadius + ", floor -" + cfg.floorBandDown + "/+" + cfg.floorBandUp)
            .addField("Match", cfg.matchMode + (cfg.matchMode == MatchMode.Smart && cfg.ignoreEnchantLevels ? " (ignore enchant levels)" : ""))
            .addField("Max kits", cfg.maxKits == 0 ? "unlimited" : String.valueOf(cfg.maxKits))
            .addField("Safety", "player-pause " + toggleStr(cfg.pauseOnPlayer) + " (" + (int) cfg.playerPauseRange + "b)")
            .addField("Auto-dc", toggleStr(cfg.autoDisconnect));
    }
}
