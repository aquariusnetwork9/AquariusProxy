package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.util.config.Config.Client.Extra.PearlLoader.Pearl;

import static com.zenith.Globals.BARITONE;
import static com.zenith.Globals.CONFIG;
import static com.zenith.command.brigadier.BlockPosArgument.blockPos;
import static com.zenith.command.brigadier.BlockPosArgument.getBlockPos;
import static com.zenith.command.brigadier.CustomStringArgumentType.getString;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;

public class PearlLoader extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("pearlLoader")
            .category(CommandCategory.MODULE)
            .description("""
           Loads player's pearls.
           
           Positions must be of interactable blocks like levers, buttons, trapdoors, etc.
           
           They should be unobstructed and reachable.
           """)
            .usageLines(
                "add <id> <x> <y> <z>",
                "del <id>",
                "load <id>",
                "list"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("pearlLoader")
            .then(literal("add").then(argument("id", wordWithChars()).then(argument("pos", blockPos()).executes(c -> {
                    String id = getString(c, "id");
                    var pos = getBlockPos(c, "pos");
                    int x = pos.x();
                    int y = pos.y();
                    int z = pos.z();
                    Pearl pearl = new Pearl(id, x, y, z);
                    var pearls = CONFIG.client.extra.pearlLoader.pearls;
                    for (var p : pearls) {
                        if (p.id().equals(id)) {
                            pearls.remove(p);
                            break;
                        }
                    }
                    pearls.add(pearl);
                    c.getSource().getEmbed()
                        .title("Pearl Added")
                        .successColor();
                }))))
            .then(literal("del").then(argument("id", wordWithChars()).executes(c -> {
                String id = getString(c, "id");
                var pearls = CONFIG.client.extra.pearlLoader.pearls;
                for (var pearl : pearls) {
                    if (pearl.id().equals(id)) {
                        pearls.remove(pearl);
                        c.getSource().getEmbed()
                            .title("Pearl Removed")
                            .successColor();
                        return OK;
                    }
                }
                c.getSource().getEmbed()
                    .title("Pearl Not Found")
                    .addField("Error", "Pearl with id: " + id + " not found.", false)
                    .errorColor();
                return OK;
            })))
            .then(literal("list").executes(c -> {
                c.getSource().getEmbed()
                    .title("Pearls List")
                    .primaryColor();
                return OK;
            }))
            .then(literal("load").then(argument("id", wordWithChars()).executes(c -> {
                String id = getString(c, "id");
                var pearls = CONFIG.client.extra.pearlLoader.pearls;
                for (var pearl : pearls) {
                    if (pearl.id().equals(id)) {
                        BARITONE.rightClickBlock(pearl.x(), pearl.y(), pearl.z())
                            .addExecutedListener(f -> {
                                c.getSource().getSource().logEmbed(c.getSource(), Embed.builder()
                                    .title("Pearl Loaded!")
                                    .addField("Pearl ID", pearl.id(), false)
                                    .successColor());
                            });
                        c.getSource().getEmbed()
                            .title("Loading Pearl")
                            .successColor();
                        return OK;
                    }
                }
                c.getSource().getEmbed()
                    .title("Pearl Not Found")
                    .addField("Error", "Pearl with id: " + id + " not found.", false)
                    .errorColor();
                return OK;
            })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
            .description(pearlsList());
    }

    private String pearlsList() {
        var pearls = CONFIG.client.extra.pearlLoader.pearls;
        if (pearls.isEmpty()) return "None";
        StringBuilder sb = new StringBuilder();
        for (var pearl : pearls) {
            sb.append("**")
                .append(pearl.id())
                .append("**: ");
            if (CONFIG.discord.reportCoords) {
                sb.append("||[")
                    .append(pearl.x()).append(", ").append(pearl.y()).append(", ").append(pearl.z())
                    .append("]||\n");
            } else {
                sb.append("coords disabled\n");
            }
        }
        return sb.toString();
    }
}
