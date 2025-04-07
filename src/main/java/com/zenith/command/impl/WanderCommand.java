package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.Command;
import com.zenith.command.CommandUsage;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;
import com.zenith.discord.Embed;
import com.zenith.module.impl.Wander;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class WanderCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("wander")
            .category(CommandCategory.MODULE)
            .description("""
            Randomly moves the player around in the world
            """)
            .usageLines(
                "on/off",
                "radius <blocks>",
                "minRadius <blocks"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("wander")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.wander.enabled = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Wander " + toggleStrCaps(CONFIG.client.extra.wander.enabled));
                MODULE.get(Wander.class).syncEnabledFromConfig();
                return OK;
            }))
            .then(literal("radius").then(argument("blocks", integer(1, 10000000)).executes(c -> {
                int radius = getInteger(c, "blocks");
                if (radius < CONFIG.client.extra.wander.minRadius) {
                    c.getSource().getEmbed()
                        .title("Error")
                        .description("Radius must be greater than minRadius");
                    return OK;
                }
                CONFIG.client.extra.wander.radius = radius;
                c.getSource().getEmbed()
                    .title("Radius Set");
                return OK;
            })))
            .then(literal("minRadius").then(argument("blocks", integer(1, 10000000)).executes(c -> {
                int radius = getInteger(c, "blocks");
                if (radius > CONFIG.client.extra.wander.radius) {
                    c.getSource().getEmbed()
                        .title("Error")
                        .description("Min Radius must be less than radius");
                    return OK;
                }
                CONFIG.client.extra.wander.minRadius = radius;
                c.getSource().getEmbed()
                    .title("Min Radius Set");
                return OK;
            })));

    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
            .addField("Wander", toggleStr(CONFIG.client.extra.wander.enabled), false)
            .addField("Radius", CONFIG.client.extra.wander.radius, false)
            .addField("Min Radius", CONFIG.client.extra.wander.minRadius, false)
            .primaryColor();
    }
}
