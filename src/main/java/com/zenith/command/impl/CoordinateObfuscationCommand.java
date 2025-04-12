package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.module.impl.CoordObfuscation;
import com.zenith.util.config.Config.Client.Extra.CoordObfuscation.ObfuscationMode;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class CoordinateObfuscationCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("coordObf")
            .category(CommandCategory.MODULE)
            .description("""
                [BETA]
                
                Obfuscates actual coordinates to players and spectators.
                
                Designed specifically for 2b2t, to let players you don't trust to visit your base/stash
                
                How it works:
                * For each player, a chunk coordinate offset is generated
                * Packets that contain coordinates are modified with that offset
                * Respawns/server switches will regenerate the offset or disconnect the player
                * Various exploits like bedrock patterns, eye of ender triangulation, and beehive data are blocked
                
                It is highly recommended to use this in conjunction with the `actionLimiter` module
                
                You should avoid allowing players to travel or respawn to 0,0, visit the worldborder, or any other known landmarks
                to avoid leaking the offset.
                
                There are multiple modes for how the offset is generated. Random is recommended as it reduces the likelihood
                and impact of the offset being discovered.
                """)
            .usageLines(
                "on/off",
                "mode <random/constant/atLocation>",
                "regenOnTpMinDistance <blocks>",
                "randomBound <chunks>",
                "randomMinOffset <blocks>",
                "randomMinSpawnDistance <blocks>",
                "constantOffset <x> <z>",
                "constantOffsetNetherTranslate on/off",
                "constantOffsetMinSpawnDistance <blocks>",
                "atLocation <x> <z>",
                "obfuscateBedrock on/off",
                "obfuscateBiomes on/off",
                "obfuscateLighting on/off",
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
                    var result = MODULE.get(CoordObfuscation.class).validateSetup();
                    if (!result.valid()) {
                        StringBuilder description = new StringBuilder();
                        for (var reason : result.invalidReasons()) {
                            if (description.length() + reason.length() > 4000) {
                                break;
                            }
                            description.append(reason).append("\n");
                        }
                        c.getSource().getEmbed()
                            .title("Validation Error")
                            .description(description.toString())
                            .errorColor();
                        return ERROR;
                    }
                }
                CONFIG.client.extra.coordObfuscation.enabled = b;
                MODULE.get(CoordObfuscation.class).syncEnabledFromConfig();
                return OK;
            }))
            .then(literal("mode")
                      .then(literal("constant").executes(c -> {
                          CONFIG.client.extra.coordObfuscation.mode = ObfuscationMode.CONSTANT_OFFSET;
                      }))
                      .then(literal("random").executes(c -> {
                          CONFIG.client.extra.coordObfuscation.mode = ObfuscationMode.RANDOM_OFFSET;
                      }))
                      .then(literal("atLocation").executes(c -> {
                          CONFIG.client.extra.coordObfuscation.mode = ObfuscationMode.AT_LOCATION;
                      })))
            .then(literal("regenOnTpMinDistance").then(argument("blocks", integer(64)).executes(c -> {
                CONFIG.client.extra.coordObfuscation.teleportOffsetRegenerateDistanceMin = c.getArgument("blocks", Integer.class);
            })))
            .then(literal("randomBound").then(argument("randomBound", integer(0, 1000000)).executes(c -> {
                CONFIG.client.extra.coordObfuscation.randomBound = c.getArgument("randomBound", Integer.class);
            })))
            .then(literal("randomMinOffset").then(argument("minOffset", integer(0, 30000000)).executes(c -> {
                CONFIG.client.extra.coordObfuscation.randomMinOffset = c.getArgument("minOffset", Integer.class);
            })))
            .then(literal("randomMinSpawnDistance").then(argument("minSpawnDistance", integer(0, 30000000)).executes(c -> {
                CONFIG.client.extra.coordObfuscation.randomMinSpawnDistance = c.getArgument("minSpawnDistance", Integer.class);
            })))
            .then(literal("constantOffset").then(argument("xOffset", integer(-30000000, 30000000)).then(argument("zOffset", integer(-30000000, 30000000)).executes(c -> {
                CONFIG.client.extra.coordObfuscation.constantOffsetX = c.getArgument("xOffset", Integer.class);
                CONFIG.client.extra.coordObfuscation.constantOffsetZ = c.getArgument("zOffset", Integer.class);
            }))))
            .then(literal("constantOffsetNetherTranslate").then(argument("toggleArg", toggle()).executes(c -> {
                CONFIG.client.extra.coordObfuscation.constantOffsetNetherTranslate = getToggle(c, "toggleArg");
            })))
            .then(literal("constantOffsetMinSpawnDistance").then(argument("chunks", integer(0, 30000000)).executes(c -> {
                CONFIG.client.extra.coordObfuscation.constantOffsetMinSpawnDistance = getInteger(c, "chunks");
            })))
            .then(literal("atLocation").then(argument("x", integer(-30000000, 30000000)).then(argument("z", integer(-30000000, 30000000)).executes(c -> {
                CONFIG.client.extra.coordObfuscation.atLocationX = c.getArgument("x", Integer.class);
                CONFIG.client.extra.coordObfuscation.atLocationZ = c.getArgument("z", Integer.class);
            }))))
            .then(literal("obfuscateBedrock").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.coordObfuscation.obfuscateBedrock = getToggle(c, "toggle");
            })))
            .then(literal("obfuscateBiomes").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.coordObfuscation.obfuscateBiomes = getToggle(c, "toggle");
            })))
            .then(literal("validateSetup").then(argument("toggleArg", toggle()).executes(c -> {
                CONFIG.client.extra.coordObfuscation.validateSetup = getToggle(c, "toggleArg");
            })))
            .then(literal("exemptProxyAccount").then(argument("toggleArg", toggle()).executes(c -> {
                CONFIG.client.extra.coordObfuscation.exemptProxyAccount = getToggle(c, "toggleArg");
            })))
            .then(literal("debugPacketLog").then(argument("toggleArg", toggle()).executes(c -> {
                CONFIG.client.extra.coordObfuscation.debugPacketLog = getToggle(c, "toggleArg");
            })));
    }

    @Override
    public void defaultEmbed(final Embed embed) {
        embed
            .title("Coordinate Obfuscation")
            .addField("Coordinate Obfuscation", toggleStr(CONFIG.client.extra.coordObfuscation.enabled))
            .addField("Mode", modeToString(CONFIG.client.extra.coordObfuscation.mode))
            .addField("Available Modes", "`constant`, `random`, `atLocation`")
            .addField("Regenerate on Teleport Min Distance", CONFIG.client.extra.coordObfuscation.teleportOffsetRegenerateDistanceMin)
            .addField("Random Bound", CONFIG.client.extra.coordObfuscation.randomBound)
            .addField("Random Minimum Offset", CONFIG.client.extra.coordObfuscation.randomMinOffset)
            .addField("Random Minimum Spawn Distance", CONFIG.client.extra.coordObfuscation.randomMinSpawnDistance)
            .addField("Constant Offset", CONFIG.client.extra.coordObfuscation.constantOffsetX + ", " + CONFIG.client.extra.coordObfuscation.constantOffsetZ)
            .addField("Constant Offset Nether Translate", toggleStr(CONFIG.client.extra.coordObfuscation.constantOffsetNetherTranslate))
            .addField("Constant Offset Minimum Spawn Distance", CONFIG.client.extra.coordObfuscation.constantOffsetMinSpawnDistance)
            .addField("At Location", CONFIG.client.extra.coordObfuscation.atLocationX + ", " + CONFIG.client.extra.coordObfuscation.atLocationZ)
            .addField("Obfuscate Bedrock", toggleStr(CONFIG.client.extra.coordObfuscation.obfuscateBedrock))
            .addField("Obfuscate Biomes", toggleStr(CONFIG.client.extra.coordObfuscation.obfuscateBiomes))
            .primaryColor();
        MODULE.get(CoordObfuscation.class).onConfigChange();
    }

    @Override
    public void defaultExecutionErrorHandler(CommandContext commandContext) {
        commandContext.getEmbed()
            .errorColor();
    }

    public String modeToString(ObfuscationMode mode) {
        return switch (mode) {
            case RANDOM_OFFSET -> "random";
            case CONSTANT_OFFSET -> "constant";
            case AT_LOCATION -> "atLocation";
        };
    }
}
