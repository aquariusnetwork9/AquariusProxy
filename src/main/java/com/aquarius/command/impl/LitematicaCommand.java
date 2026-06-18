package com.aquarius.command.impl;

import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.discord.Embed;
import com.aquarius.discord.Panels;
import com.aquarius.feature.litematica.BuildPlan;
import com.aquarius.feature.litematica.Schematic;
import com.aquarius.module.impl.LitematicaBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.List;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.DISCORD;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;

public class LitematicaCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("litematica")
            .category(CommandCategory.MODULE)
            .aliases("lite", "schematic")
            .description("""
                Auto-build a structure from a Litematica .litematic (or vanilla .nbt) schematic: paths with Baritone,
                places blocks, and restocks materials from nearby chests / placed shulker boxes. v1 builds full
                solid blocks only (orientation / block-states are not reproduced).
                """)
            .usageLines(
                "on/off",
                "load <file>             (parse a schematic from the schematics dir)",
                "list                    (list available schematic files)",
                "origin <x> <y> <z>      (where the schematic's min corner is built)",
                "origin here             (use the bot's current position)",
                "start                   (begin building)",
                "pause                   (soft-pause; resume with start)",
                "stop                    (stop and disarm)",
                "status                  (schematic, progress, missing materials)",
                "restock chests <on/off>",
                "restock shulkers <on/off>",
                "restock radius <blocks>",
                "panel                   (open the Discord control panel)"
            )
            .build();
    }

    private LitematicaBuilder module() {
        return MODULE.get(LitematicaBuilder.class);
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("litematica")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.litematica.enabled = getToggle(c, "toggle");
                module().syncEnabledFromConfig();
                c.getSource().getEmbed().title("Litematica " + toggleStrCaps(CONFIG.client.extra.litematica.enabled));
            }))
            .then(literal("load").then(argument("file", greedyString()).executes(c -> {
                String file = getString(c, "file").trim();
                String err = module().load(file);
                if (err != null) {
                    c.getSource().getEmbed().title("Load failed").addField("Error", err, false).errorColor();
                } else {
                    Schematic s = module().schematic();
                    c.getSource().getEmbed()
                        .title("Loaded " + (s != null ? s.name() : file))
                        .addField("Size", s == null ? "?" : s.sizeX() + " × " + s.sizeY() + " × " + s.sizeZ(), true)
                        .addField("Blocks", s == null ? "?" : String.valueOf(s.totalBlocks()), true)
                        .addField("Skipped", s == null ? "?" : String.valueOf(s.skipped()), true)
                        .successColor();
                }
            })))
            .then(literal("list").executes(c -> {
                List<String> files = module().listSchematics();
                c.getSource().getEmbed()
                    .title("Schematics (" + files.size() + ")")
                    .description(files.isEmpty()
                        ? "No schematics in `" + CONFIG.client.extra.litematica.schematicsDir + "`. Upload a `.litematic` / `.nbt` file to Discord, or drop one in that folder."
                        : String.join("\n", files))
                    .primaryColor();
            }))
            .then(literal("origin")
                .then(literal("here").executes(c -> {
                    var cfg = CONFIG.client.extra.litematica;
                    cfg.originX = (int) Math.floor(CACHE.getPlayerCache().getX());
                    cfg.originY = (int) Math.floor(CACHE.getPlayerCache().getY());
                    cfg.originZ = (int) Math.floor(CACHE.getPlayerCache().getZ());
                    cfg.originSet = true;
                    c.getSource().getEmbed().title("Origin set to " + cfg.originX + " " + cfg.originY + " " + cfg.originZ);
                }))
                .then(argument("x", integer()).then(argument("y", integer()).then(argument("z", integer()).executes(c -> {
                    var cfg = CONFIG.client.extra.litematica;
                    cfg.originX = getInteger(c, "x");
                    cfg.originY = getInteger(c, "y");
                    cfg.originZ = getInteger(c, "z");
                    cfg.originSet = true;
                    c.getSource().getEmbed().title("Origin set to " + cfg.originX + " " + cfg.originY + " " + cfg.originZ);
                })))))
            .then(literal("start").executes(c -> {
                String err = module().start();
                if (err != null) c.getSource().getEmbed().title("Can't start").addField("Error", err, false).errorColor();
                else c.getSource().getEmbed().title("Build started").successColor();
            }))
            .then(literal("pause").executes(c -> {
                module().pause();
                c.getSource().getEmbed().title("Build paused");
            }))
            .then(literal("stop").executes(c -> {
                module().stop();
                c.getSource().getEmbed().title("Build stopped");
            }))
            .then(literal("status").executes(c -> { statusEmbed(c.getSource().getEmbed()); }))
            .then(literal("restock")
                .then(literal("chests").then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.litematica.restockFromChests = getToggle(c, "toggle");
                    c.getSource().getEmbed().title("Restock from chests " + toggleStrCaps(CONFIG.client.extra.litematica.restockFromChests));
                })))
                .then(literal("shulkers").then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.litematica.restockFromShulkers = getToggle(c, "toggle");
                    c.getSource().getEmbed().title("Restock from shulkers " + toggleStrCaps(CONFIG.client.extra.litematica.restockFromShulkers));
                })))
                .then(literal("radius").then(argument("blocks", integer(1)).executes(c -> {
                    CONFIG.client.extra.litematica.restockRadius = getInteger(c, "blocks");
                    c.getSource().getEmbed().title("Restock radius = " + CONFIG.client.extra.litematica.restockRadius + " blocks");
                }))))
            .then(literal("panel").executes(c -> {
                boolean posted = DISCORD.openPanel(Panels.LITEMATICA);
                c.getSource().getEmbed().title(posted ? "Litematica panel posted" : "Discord not connected");
            }));
    }

    private void statusEmbed(Embed embed) {
        var cfg = CONFIG.client.extra.litematica;
        LitematicaBuilder m = module();
        Schematic s = m.schematic();
        BuildPlan p = m.plan();
        embed.title("Litematica Builder").primaryColor()
            .addField("Phase", m.phase().name(), true)
            .addField("Schematic", s != null ? s.name() : (cfg.schematicFile.isBlank() ? "none" : cfg.schematicFile), true)
            .addField("Origin", cfg.originSet ? cfg.originX + " " + cfg.originY + " " + cfg.originZ : "not set", true)
            .addField("Restock", "chests " + toggleStr(cfg.restockFromChests) + ", shulkers " + toggleStr(cfg.restockFromShulkers)
                + " (r=" + cfg.restockRadius + ")", false);
        if (p != null) {
            embed.addField("Progress", p.doneCount() + " / " + p.total(), true);
            List<Schematic.MaterialEntry> missing = p.remainingMaterials();
            if (!missing.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(8, missing.size()); i++) {
                    sb.append(missing.get(i).item()).append("  ×").append(missing.get(i).count()).append('\n');
                }
                embed.addField("Missing materials", sb.toString(), false);
            }
        }
    }
}
