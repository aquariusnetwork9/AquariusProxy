package com.aquarius.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.Proxy;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.feature.player.World;
import com.aquarius.feature.player.raycast.RaycastHelper;

import static com.aquarius.Globals.BOT;
import static com.aquarius.Globals.CONFIG;

public class RaycastCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("raycast")
            .category(CommandCategory.INFO)
            .description("Debug testing command. Gets the block or entity the player is currently looking at.")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("raycast").executes(c -> {
                if (Proxy.getInstance().hasActivePlayer()) BOT.syncFromCache(true);
                var result = RaycastHelper.playerBlockOrEntityRaycast(BOT.getBlockReachDistance(), BOT.getEntityInteractDistance());
                var embed = c.getSource().getEmbed();
                embed.title("Raycast Result")
                    .addField("Hit", result.hit(), false)
                    .primaryColor();
                if (result.isBlock()) {
                    embed.addField("Block", result.block().block().toString(), false);
                    if (CONFIG.discord.reportCoords) {
                        embed.addField("Pos", ("||[" + result.block().x() + ", " + result.block().y() + ", " + result.block().z()) + "]||", false);
                    }
                    embed.addField("Direction", result.block().direction().name(), false);
                    if (result.hit() && CONFIG.discord.reportCoords) {
                        embed.addField("State", "||" + World.getBlockState(result.block().x(), result.block().y(), result.block().z()).toString() + "||", false);
                    }
                } else if (result.isEntity()) {
                    var type = result.entity().entityType();
                    embed.addField("Entity", type != null ? type : "N/A", false);
                }})
            .then(literal("e").executes(c -> {
                if (Proxy.getInstance().hasActivePlayer()) BOT.syncFromCache(true);
                var result = RaycastHelper.playerEntityRaycast(BOT.getEntityInteractDistance());
                c.getSource().getEmbed()
                    .title("Raycast Result")
                    .addField("Hit", result.hit(), false)
                    .addField("Entity", result.entity() != null ? result.entityType() != null ? result.entityType() : "N/A" : "N/A", false)
                    .addField("ID", result.entity() != null ? result.entity().getEntityId() : "N/A", false)
                    .primaryColor();
            }))
            .then(literal("b").executes(c -> {
                if (Proxy.getInstance().hasActivePlayer()) BOT.syncFromCache(true);
                var result = RaycastHelper.playerBlockRaycast(BOT.getBlockReachDistance(), false);
                c.getSource().getEmbed()
                    .title("Raycast Result")
                    .addField("Hit", result.hit(), false)
                    .addField("Block", result.block().toString(), false);
                if (CONFIG.discord.reportCoords) {
                    c.getSource().getEmbed().addField("Pos", "||[ " + result.x() + ", " + result.y() + ", " + result.z() + "]||", false);
                }
                c.getSource().getEmbed().addField("Direction", result.direction().name(), false)
                    .primaryColor();
                if (result.hit() && CONFIG.discord.reportCoords) {
                    c.getSource().getEmbed().addField("State", "||" + World.getBlockState(result.x(), result.y(), result.z()).toString() + "||", false);
                }
            }));
    }
}
