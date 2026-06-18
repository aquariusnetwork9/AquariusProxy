package com.aquarius.command.impl;

import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.module.impl.Boat;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.aquarius.Globals.MODULE;

/**
 * {@code .boat} — drive a boat the bot is seated in. Phase 1 = manual steering to validate the physics port; the
 * autopilot ({@code .boat goto}) steers toward an open-water coordinate. See {@link Boat}.
 */
public class BoatCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("boat")
            .category(CommandCategory.MODULE)
            .description("""
                Drive a boat the bot is already seated in (a controlling player must mount it first).
                Simulates Minecraft boat physics server-side and reports it via MoveVehicle.
                """)
            .usageLines(
                "mount               (right-click the nearest empty boat to seat the bot, then arm control)",
                "on/off              (enable/disable boat control)",
                "goto <x> <z>        (autopilot: route around land to the coords, then stop)",
                "avoid <on|off>      (route around land vs. steer straight at the target; default on)",
                "radius <8-256>      (A* search window half-size in blocks; default 64)",
                "fwd                 (manual: hold forward)",
                "back                (manual: hold reverse)",
                "left                (manual: hold turn left)",
                "right               (manual: hold turn right)",
                "fwdleft / fwdright  (manual: forward while turning)",
                "stop                (release manual input / cancel goto; stays enabled)"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("boat")
            .then(literal("mount").executes(c -> {
                boolean ok = MODULE.get(Boat.class).mountNearestBoat();
                c.getSource().getEmbed().title(ok ? "Boat mount" : "Boat mount failed")
                    .description(ok
                        ? "Right-clicked the nearest empty boat + armed control. Now `.boat goto <x> <z>`."
                        : "No empty boat in range, or already seated. Get within ~8 blocks of a boat.");
            }))
            .then(literal("on").executes(c -> {
                MODULE.get(Boat.class).enable();
                c.getSource().getEmbed().title("Boat control ON")
                    .description("Seated in a boat? Use `.boat goto <x> <z>` or the manual steering commands.");
            }))
            .then(literal("off").executes(c -> {
                MODULE.get(Boat.class).disable();
                c.getSource().getEmbed().title("Boat control OFF");
            }))
            .then(literal("goto")
                .then(argument("x", doubleArg())
                    .then(argument("z", doubleArg()).executes(c -> {
                        double x = getDouble(c, "x");
                        double z = getDouble(c, "z");
                        Boat boat = MODULE.get(Boat.class);
                        boat.enable();
                        boat.goTo(x, z);
                        c.getSource().getEmbed().title("Boat goto")
                            .description((boat.isAvoid() ? "Routing around land to " : "Steering straight to ")
                                + (int) x + ", " + (int) z);
                    }))))
            .then(literal("avoid")
                .then(literal("on").executes(c -> {
                    MODULE.get(Boat.class).setAvoid(true);
                    c.getSource().getEmbed().title("Boat avoidance ON")
                        .description("Routing around land (blocks breaching the water surface).");
                }))
                .then(literal("off").executes(c -> {
                    MODULE.get(Boat.class).setAvoid(false);
                    c.getSource().getEmbed().title("Boat avoidance OFF")
                        .description("Steering straight at the target — no obstacle avoidance.");
                })))
            .then(literal("radius")
                .then(argument("blocks", integer(8, 256)).executes(c -> {
                    Boat boat = MODULE.get(Boat.class);
                    boat.setPlanRadius(getInteger(c, "blocks"));
                    c.getSource().getEmbed().title("Boat search radius")
                        .description("A* window half-size set to " + boat.getPlanRadius() + " blocks.");
                })))
            .then(literal("fwd").executes(c -> { manual(c.getSource(), true, false, false, false, "forward"); }))
            .then(literal("back").executes(c -> { manual(c.getSource(), false, true, false, false, "reverse"); }))
            .then(literal("left").executes(c -> { manual(c.getSource(), false, false, true, false, "turning left"); }))
            .then(literal("right").executes(c -> { manual(c.getSource(), false, false, false, true, "turning right"); }))
            .then(literal("fwdleft").executes(c -> { manual(c.getSource(), true, false, true, false, "forward + left"); }))
            .then(literal("fwdright").executes(c -> { manual(c.getSource(), true, false, false, true, "forward + right"); }))
            .then(literal("stop").executes(c -> {
                MODULE.get(Boat.class).stopSteering();
                c.getSource().getEmbed().title("Boat stopped").description("Released input (still enabled).");
            }));
    }

    private void manual(CommandContext src, boolean f, boolean b, boolean l, boolean r, String label) {
        Boat boat = MODULE.get(Boat.class);
        boat.enable();
        boat.setManual(f, b, l, r);
        src.getEmbed().title("Boat manual").description("Holding: " + label);
    }
}
