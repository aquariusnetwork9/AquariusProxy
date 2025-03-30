package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.Command;
import com.zenith.command.CommandUsage;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;
import com.zenith.discord.Embed;
import com.zenith.module.impl.CoordObfuscator;
import com.zenith.util.Config.Client.Extra.CoordObfuscation.ObfuscationMode;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Shared.CONFIG;
import static com.zenith.Shared.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class CoordinateObfuscationCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("coordObf")
            .category(CommandCategory.MODULE)
            .description("Obfuscates actual coordinates to players and spectators")
            .usageLines(
                "on/off",
                "mode <mode>",
                "regenOnTp on/off",
                "regenOnTpMinDistance <blocks>",
                "randomBound <chunks>",
                "randomMinOffset <blocks>",
                "randomMinSpawnDistance <blocks>",
                "constantOffset <x blocks> <z blocks>",
                "constantOffsetNetherTranslate on/off",
                "constantOffsetMinSpawnDistance <blocks>",
                "atLocation <x> <z>",
                "validateSetup on/off"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("coordobf").requires(Command::validateAccountOwner)
            .then(argument("toggle", toggle()).executes(c -> {
                var b = getToggle(c, "toggle");
                if (b && CONFIG.client.extra.coordObfuscation.validateSetup) {
                    var result = MODULE.get(CoordObfuscator.class).validateSetup();
                    if (!result.valid()) {
                        var description = String.join("\n", result.invalidReasons());
                        if (description.length() > 4000) {
                            description = description.substring(0, 4000) + "...";
                        }
                        c.getSource().getEmbed()
                            .title("Validation Error")
                            .description(description)
                            .errorColor();
                        return ERROR;
                    }
                }
                CONFIG.client.extra.coordObfuscation.enabled = b;
                MODULE.get(CoordObfuscator.class).syncEnabledFromConfig();
                return OK;
            }))
            .then(literal("mode")
                      .then(literal("constant").executes(c -> {
                          CONFIG.client.extra.coordObfuscation.mode = ObfuscationMode.CONSTANT_OFFSET;
                          return OK;
                      }))
                      .then(literal("random").executes(c -> {
                          CONFIG.client.extra.coordObfuscation.mode = ObfuscationMode.RANDOM_OFFSET;
                          return OK;
                      }))
                      .then(literal("atlocation").executes(c -> {
                          CONFIG.client.extra.coordObfuscation.mode = ObfuscationMode.AT_LOCATION;
                          return OK;
                      })))
//            .then(literal("regenOnTp").then(argument("toggle", toggle()).executes(c -> {
//                CONFIG.client.extra.coordObfuscation.regenerateOnDistantTeleport = getToggle(c, "toggle");
//                return OK;
//            })))
//            .then(literal("regenOnTpMinDistance").then(argument("blocks", integer(64)).executes(c -> {
//                CONFIG.client.extra.coordObfuscation.regenerateDistanceMin = c.getArgument("blocks", Integer.class);
//                return OK;
//            })))
            .then(literal("randomBound").then(argument("randomBound", integer(0, 1000000)).executes(c -> {
                CONFIG.client.extra.coordObfuscation.randomBound = c.getArgument("randomBound", Integer.class);
                return OK;
            })))
            .then(literal("randomMinOffset").then(argument("minOffset", integer(0, 30000000)).executes(c -> {
                CONFIG.client.extra.coordObfuscation.randomMinOffset = c.getArgument("minOffset", Integer.class);
                return OK;
            })))
            .then(literal("randomMinSpawnDistance").then(argument("minSpawnDistance", integer(0, 30000000)).executes(c -> {
                CONFIG.client.extra.coordObfuscation.randomMinSpawnDistance = c.getArgument("minSpawnDistance", Integer.class);
                return OK;
            })))
            .then(literal("constantOffset").then(argument("xOffset", integer(-30000000, 30000000)).then(argument("zOffset", integer(-30000000, 30000000)).executes(c -> {
                CONFIG.client.extra.coordObfuscation.constantOffsetX = c.getArgument("xOffset", Integer.class);
                CONFIG.client.extra.coordObfuscation.constantOffsetZ = c.getArgument("zOffset", Integer.class);
                return OK;
            }))))
            .then(literal("constantOffsetNetherTranslate").then(argument("toggleArg", toggle()).executes(c -> {
                CONFIG.client.extra.coordObfuscation.constantOffsetNetherTranslate = getToggle(c, "toggleArg");
                return OK;
            })))
            .then(literal("constantOffsetMinSpawnDistance").then(argument("chunks", integer(0, 30000000)).executes(c -> {
                CONFIG.client.extra.coordObfuscation.constantOffsetMinSpawnDistance = getInteger(c, "chunks");
                return OK;
            })))
            .then(literal("atLocation").then(argument("x", integer(-30000000, 30000000)).then(argument("z", integer(-30000000, 30000000)).executes(c -> {
                CONFIG.client.extra.coordObfuscation.atLocationX = c.getArgument("x", Integer.class);
                CONFIG.client.extra.coordObfuscation.atLocationZ = c.getArgument("z", Integer.class);
                return OK;
            }))))
            .then(literal("validateSetup").then(argument("toggleArg", toggle()).executes(c -> {
                CONFIG.client.extra.coordObfuscation.validateSetup = getToggle(c, "toggleArg");
                c.getSource().getEmbed()
                    .addField("Validate Setup", toggleStr(CONFIG.client.extra.coordObfuscation.validateSetup), false);
                return OK;
            })));
    }

    @Override
    public void postPopulate(final Embed embed) {
        embed
            .title("Coordinate Obfuscation")
            .addField("Coordinate Obfuscation", toggleStr(CONFIG.client.extra.coordObfuscation.enabled), false)
            .addField("Mode", CONFIG.client.extra.coordObfuscation.mode.name(), true)
            .addField("Available Modes", "`constant`, `random`, `atLocation`", true)
//            .addField("Regenerate On Teleport", toggleStr(CONFIG.client.extra.coordObfuscation.regenerateOnDistantTeleport), true)
            .addField("Random Bound", CONFIG.client.extra.coordObfuscation.randomBound, true)
            .addField("Random Minimum Offset", CONFIG.client.extra.coordObfuscation.randomMinOffset, true)
            .addField("Random Minimum Spawn Distance", CONFIG.client.extra.coordObfuscation.randomMinSpawnDistance, true)
            .addField("Constant Offset", CONFIG.client.extra.coordObfuscation.constantOffsetX + ", " + CONFIG.client.extra.coordObfuscation.constantOffsetZ, true)
            .addField("Constant Offset Nether Translate", toggleStr(CONFIG.client.extra.coordObfuscation.constantOffsetNetherTranslate), true)
            .addField("Constant Offset Minimum Spawn Distance", CONFIG.client.extra.coordObfuscation.constantOffsetMinSpawnDistance, true)
            .addField("At Location", CONFIG.client.extra.coordObfuscation.atLocationX + ", " + CONFIG.client.extra.coordObfuscation.atLocationZ, true)
            .primaryColor();
        MODULE.get(CoordObfuscator.class).onConfigChange();
    }
}
