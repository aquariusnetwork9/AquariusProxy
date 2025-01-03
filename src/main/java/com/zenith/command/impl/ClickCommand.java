package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.Command;
import com.zenith.command.CommandUsage;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;
import com.zenith.feature.world.Input;
import com.zenith.module.impl.PlayerSimulation;

import static com.mojang.brigadier.arguments.FloatArgumentType.floatArg;
import static com.mojang.brigadier.arguments.FloatArgumentType.getFloat;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Shared.MODULE;
import static com.zenith.Shared.PATHING;
import static java.util.Arrays.asList;

public class ClickCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.args(
            "click",
            CommandCategory.MODULE,
            """
            Simulates a click to the block or entity in front of you
            """,
            asList(
                "left",
                "left hold",
                "right",
                "right hold",
                "right hold <mainHand/offHand/alternate>",
                "right hold interval <ticks>",
                "reach add <float>",
                "stop"
            )
        );
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("click")
            .then(literal("stop").executes(c -> {
                if (!Proxy.getInstance().isConnected()) {
                    c.getSource().getEmbed()
                        .title("Not Connected")
                        .errorColor();
                    return OK;
                }
                MODULE.get(PlayerSimulation.class).holdLeftClickOverride = false;
                MODULE.get(PlayerSimulation.class).holdRightClickOverride = false;
                c.getSource().getEmbed()
                    .title("Click Hold Off")
                    .primaryColor();
                return OK;
            }))
            .then(literal("left").executes(c -> {
                if (!Proxy.getInstance().isConnected()) {
                    c.getSource().getEmbed()
                        .title("Not Connected")
                        .errorColor();
                    return OK;
                }
                var input = new Input();
                input.leftClick = true;
                PATHING.move(input, 100000);
                c.getSource().getEmbed()
                    .title("Left Clicked")
                    .primaryColor();
                return 1;
            })
                      .then(literal("hold").executes(c -> {
                          if (!Proxy.getInstance().isConnected()) {
                              c.getSource().getEmbed()
                                  .title("Not Connected")
                                  .errorColor();
                              return OK;
                          }
                          MODULE.get(PlayerSimulation.class).holdLeftClickOverride = true;
                          MODULE.get(PlayerSimulation.class).holdRightClickOverride = false;
                          c.getSource().getEmbed()
                              .title("Left Click Hold")
                              .primaryColor();
                          return OK;
                      })))
            .then(literal("right").executes(c -> {
                if (!Proxy.getInstance().isConnected()) {
                    c.getSource().getEmbed()
                        .title("Not Connected")
                        .errorColor();
                    return OK;
                }
                var input = new Input();
                input.rightClick = true;
                PATHING.move(input, 100000);
                c.getSource().getEmbed()
                    .title("Right Clicked")
                    .primaryColor();
                return 1;
            })
                      .then(literal("hold")
                                .executes(c -> {
                                    if (!Proxy.getInstance().isConnected()) {
                                        c.getSource().getEmbed()
                                            .title("Not Connected")
                                            .errorColor();
                                        return OK;
                                    }
                                    MODULE.get(PlayerSimulation.class).holdLeftClickOverride = false;
                                    MODULE.get(PlayerSimulation.class).holdRightClickOverride = true;
                                    MODULE.get(PlayerSimulation.class).holdRightClickMode = PlayerSimulation.HoldRightClickMode.MAIN_HAND;
                                    c.getSource().getEmbed()
                                        .title("Right Click Hold")
                                        .primaryColor();
                                    return OK;
                                })
                                .then(literal("mainHand").executes(c -> {
                                    if (!Proxy.getInstance().isConnected()) {
                                        c.getSource().getEmbed()
                                            .title("Not Connected")
                                            .errorColor();
                                        return OK;
                                    }
                                    MODULE.get(PlayerSimulation.class).holdLeftClickOverride = false;
                                    MODULE.get(PlayerSimulation.class).holdRightClickOverride = true;
                                    MODULE.get(PlayerSimulation.class).holdRightClickMode = PlayerSimulation.HoldRightClickMode.MAIN_HAND;
                                    c.getSource().getEmbed()
                                        .title("Right Click Hold (Main Hand)")
                                        .primaryColor();
                                    return OK;
                                }))
                                .then(literal("offHand").executes(c -> {
                                    if (!Proxy.getInstance().isConnected()) {
                                        c.getSource().getEmbed()
                                            .title("Not Connected")
                                            .errorColor();
                                        return OK;
                                    }
                                    MODULE.get(PlayerSimulation.class).holdLeftClickOverride = false;
                                    MODULE.get(PlayerSimulation.class).holdRightClickOverride = true;
                                    MODULE.get(PlayerSimulation.class).holdRightClickMode = PlayerSimulation.HoldRightClickMode.OFF_HAND;
                                    c.getSource().getEmbed()
                                        .title("Right Click Hold (Offhand)")
                                        .primaryColor();
                                    return OK;
                                }))
                                .then(literal("alternate").executes(c -> {
                                    if (!Proxy.getInstance().isConnected()) {
                                        c.getSource().getEmbed()
                                            .title("Not Connected")
                                            .errorColor();
                                        return OK;
                                    }
                                    MODULE.get(PlayerSimulation.class).holdLeftClickOverride = false;
                                    MODULE.get(PlayerSimulation.class).holdRightClickOverride = true;
                                    MODULE.get(PlayerSimulation.class).holdRightClickMode = PlayerSimulation.HoldRightClickMode.ALTERNATE_HANDS;
                                    c.getSource().getEmbed()
                                        .title("Right Click Hold (Alternate)")
                                        .primaryColor();
                                    return OK;
                                }))
                                .then(literal("interval").then(argument("ticks", integer(0, 100)).executes(c -> {
                                    MODULE.get(PlayerSimulation.class).holdRightClickInterval = getInteger(c, "ticks");
                                    c.getSource().getEmbed()
                                        .title("Right Click Hold Interval Set")
                                        .addField("Ticks", MODULE.get(PlayerSimulation.class).holdRightClickInterval, false)
                                        .primaryColor();
                                    return OK;
                                })))))
            .then(literal("reach").then(literal("add").then(argument("reach", floatArg(0, 10)).executes(c -> {
                float f = getFloat(c, "reach");
                MODULE.get(PlayerSimulation.class).additionalBlockReach = f;
                c.getSource().getEmbed()
                    .title("Additional Reach Set")
                    .addField("Reach", f, false)
                    .primaryColor();
                return OK;
            }))));
    }
}
