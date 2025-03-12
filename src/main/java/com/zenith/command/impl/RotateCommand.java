package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.Command;
import com.zenith.command.CommandUsage;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;
import com.zenith.feature.world.InputRequest;

import static com.mojang.brigadier.arguments.FloatArgumentType.floatArg;
import static com.mojang.brigadier.arguments.FloatArgumentType.getFloat;
import static com.zenith.Shared.INPUTS;

public class RotateCommand extends Command {
    private static final int MOVE_PRIORITY = 1000000;

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("rotate")
            .category(CommandCategory.MODULE)
            .description("""
            Rotates the bot in-game.
            
            Note that many other modules can change the player's rotation after this command is executed.
            """)
            .usageLines(
                "<yaw> <pitch>",
                "yaw <yaw>",
                "pitch <pitch>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("rotate")
            .then(literal("yaw").then(argument("yaw", floatArg(-180, 180)).executes(c -> {
                float yaw = 0f;// getFloat(c, "yaw");
                if (!Proxy.getInstance().isConnected()) {
                    c.getSource().getEmbed()
                        .title("Error")
                        .errorColor()
                        .description("Not connected to a server");
                    return OK;
                }
                if (Proxy.getInstance().hasActivePlayer()) {
                    c.getSource().getEmbed()
                        .title("Error")
                        .errorColor()
                        .description("Cannot rotate while player is controlling");
                    return OK;
                }
                INPUTS.submit(InputRequest.builder()
                                  .yaw(yaw)
                                  .priority(MOVE_PRIORITY)
                                  .build());
                c.getSource().getEmbed()
                    .title("Rotated")
                    .successColor();
                return OK;
            })))
            .then(literal("pitch").then(argument("pitch", floatArg(-90, 90)).executes(c -> {
                float pitch = getFloat(c, "pitch");
                if (!Proxy.getInstance().isConnected()) {
                    c.getSource().getEmbed()
                        .title("Error")
                        .errorColor()
                        .description("Not connected to a server");
                    return OK;
                }
                if (Proxy.getInstance().hasActivePlayer()) {
                    c.getSource().getEmbed()
                        .title("Error")
                        .errorColor()
                        .description("Cannot rotate while player is controlling");
                    return OK;
                }
                INPUTS.submit(InputRequest.builder()
                                  .pitch(pitch)
                                  .priority(MOVE_PRIORITY)
                                  .build());
                c.getSource().getEmbed()
                    .title("Rotated")
                    .successColor();
                return OK;
            })))
            .then(argument("yawArg", floatArg(-180, 180)).then(argument("pitchArg", floatArg(-90, 90)).executes(c -> {
                float yaw = getFloat(c, "yawArg");
                float pitch = getFloat(c, "pitchArg");
                if (!Proxy.getInstance().isConnected()) {
                    c.getSource().getEmbed()
                        .title("Error")
                        .errorColor()
                        .description("Not connected to a server");
                    return OK;
                }
                if (Proxy.getInstance().hasActivePlayer()) {
                    c.getSource().getEmbed()
                        .title("Error")
                        .errorColor()
                        .description("Cannot rotate while player is controlling");
                    return OK;
                }
                INPUTS.submit(InputRequest.builder()
                                  .yaw(yaw)
                                  .pitch(pitch)
                                  .priority(MOVE_PRIORITY)
                                  .build());
                c.getSource().getEmbed()
                    .title("Rotated")
                    .successColor();
                return OK;
            })));
    }
}
