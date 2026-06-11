package com.aquarius.command.impl;

import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.module.impl.PearlDrop;
import com.aquarius.module.impl.PearlDrop.StasisChamber;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;

public class PearlDropCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("pearldrop")
            .category(CommandCategory.MODULE)
            .aliases("pd")
            .description("""
                Deposit ender pearls into pearl stasis chambers (a water/bubble column with soul sand at the
                bottom and a trapdoor on top). The bot walks to the rim, sneak-overhangs the column, aims at the
                soul-sand centre, throws a pearl in, and backs off. Needs ender pearls in its inventory.
                """)
            .usageLines(
                "on/off",
                "scan [radius]          (find chambers in loaded chunks; lists them with indices)",
                "list                   (reprint the last scan)",
                "drop <x> <y> <z> [n]   (deposit into the chamber near these coords; auto-picks an empty one)",
                "pick <indices>         (deposit into scanned chambers by index, e.g. 'pick 1 3 4')",
                "all                    (deposit into every empty chamber from the last scan)",
                "count <n>              (pearls to deposit per chamber)",
                "stop                   (cancel the current run)",
                "depth <n>              (min water depth to count as a chamber)",
                "radius <n>             (scan horizontal radius)",
                "yrange <n>             (scan vertical half-range around the bot)",
                "tolerance <n>          (search radius for 'drop <coords>')",
                "spacing <ticks>        (ticks between throws into one chamber)",
                "eyeheight <d>          (eye height used to aim; ~1.27 sneaking)",
                "priority <n>           (input priority for the sneak/aim/throw control)",
                "closetrapdoor <on/off> (shut an open trapdoor before depositing)"
            )
            .build();
    }

    private PearlDrop module() {
        return MODULE.get(PearlDrop.class);
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("pearldrop")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.pearlDrop.enabled = getToggle(c, "toggle");
                module().syncEnabledFromConfig();
                c.getSource().getEmbed().title("PearlDrop " + toggleStrCaps(CONFIG.client.extra.pearlDrop.enabled));
            }))
            .then(literal("scan")
                .executes(c -> { return runScan(c, CONFIG.client.extra.pearlDrop.scanRadius); })
                .then(argument("radius", integer(1)).executes(c -> { return runScan(c, getInteger(c, "radius")); })))
            .then(literal("list").executes(c -> { return printScan(c, module().getLastScan()); }))
            .then(literal("drop")
                .then(argument("x", integer())
                    .then(argument("y", integer())
                        .then(argument("z", integer())
                            .executes(c -> { return doDrop(c, getInteger(c, "x"), getInteger(c, "y"), getInteger(c, "z"),
                                CONFIG.client.extra.pearlDrop.pearlsPerChamber); })
                            .then(argument("count", integer(1)).executes(c -> { return doDrop(c,
                                getInteger(c, "x"), getInteger(c, "y"), getInteger(c, "z"), getInteger(c, "count")); }))))))
            .then(literal("pick").then(argument("indices", greedyString()).executes(c -> { return doPick(c, getString(c, "indices")); })))
            .then(literal("all").executes(c -> {
                int n = module().dropAllEmpty(CONFIG.client.extra.pearlDrop.pearlsPerChamber);
                if (n == 0) c.getSource().getEmbed().title("PearlDrop").description("No empty, reachable chambers in the last scan. Run /pd scan first.").errorColor();
                else c.getSource().getEmbed().title("PearlDrop").description("Queued " + n + " chamber(s) for deposit.").successColor();
            }))
            .then(literal("count").then(argument("n", integer(1)).executes(c -> {
                CONFIG.client.extra.pearlDrop.pearlsPerChamber = getInteger(c, "n");
                c.getSource().getEmbed().title("PearlDrop pearls per chamber = " + CONFIG.client.extra.pearlDrop.pearlsPerChamber);
            })))
            .then(literal("stop").executes(c -> {
                module().stopAll();
                c.getSource().getEmbed().title("PearlDrop stopped");
            }))
            .then(literal("depth").then(argument("n", integer(1)).executes(c -> {
                CONFIG.client.extra.pearlDrop.minWaterDepth = getInteger(c, "n");
                c.getSource().getEmbed().title("PearlDrop min water depth = " + CONFIG.client.extra.pearlDrop.minWaterDepth);
            })))
            .then(literal("radius").then(argument("n", integer(1)).executes(c -> {
                CONFIG.client.extra.pearlDrop.scanRadius = getInteger(c, "n");
                c.getSource().getEmbed().title("PearlDrop scan radius = " + CONFIG.client.extra.pearlDrop.scanRadius);
            })))
            .then(literal("yrange").then(argument("n", integer(0)).executes(c -> {
                CONFIG.client.extra.pearlDrop.scanYRange = getInteger(c, "n");
                c.getSource().getEmbed().title("PearlDrop scan Y range = +/-" + CONFIG.client.extra.pearlDrop.scanYRange);
            })))
            .then(literal("tolerance").then(argument("n", integer(0)).executes(c -> {
                CONFIG.client.extra.pearlDrop.coordTolerance = getInteger(c, "n");
                c.getSource().getEmbed().title("PearlDrop coord tolerance = " + CONFIG.client.extra.pearlDrop.coordTolerance);
            })))
            .then(literal("spacing").then(argument("ticks", integer(0)).executes(c -> {
                CONFIG.client.extra.pearlDrop.throwSpacingTicks = getInteger(c, "ticks");
                c.getSource().getEmbed().title("PearlDrop throw spacing = " + CONFIG.client.extra.pearlDrop.throwSpacingTicks + " ticks");
            })))
            .then(literal("eyeheight").then(argument("d", doubleArg()).executes(c -> {
                CONFIG.client.extra.pearlDrop.eyeHeight = getDouble(c, "d");
                c.getSource().getEmbed().title("PearlDrop eye height = " + CONFIG.client.extra.pearlDrop.eyeHeight);
            })))
            .then(literal("priority").then(argument("n", integer()).executes(c -> {
                CONFIG.client.extra.pearlDrop.inputPriority = getInteger(c, "n");
                c.getSource().getEmbed().title("PearlDrop input priority = " + CONFIG.client.extra.pearlDrop.inputPriority);
            })))
            .then(literal("closetrapdoor").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.pearlDrop.closeOpenTrapdoor = getToggle(c, "toggle");
                c.getSource().getEmbed().title("PearlDrop close-open-trapdoor " + toggleStrCaps(CONFIG.client.extra.pearlDrop.closeOpenTrapdoor));
            })));
    }

    private int runScan(com.mojang.brigadier.context.CommandContext<CommandContext> c, int radius) {
        List<StasisChamber> found = module().scan(radius);
        return printScan(c, found);
    }

    private int printScan(com.mojang.brigadier.context.CommandContext<CommandContext> c, List<StasisChamber> chambers) {
        if (chambers.isEmpty()) {
            c.getSource().getEmbed().title("PearlDrop scan").description("No stasis chambers found in loaded chunks.").primaryColor();
            return OK;
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        int empty = 0;
        for (StasisChamber ch : chambers) {
            if (!module().isOccupied(ch) && module().hasRim(ch)) empty++;
            sb.append(i++).append(". ").append(module().describe(ch)).append("\n");
        }
        c.getSource().getEmbed()
            .title("PearlDrop scan — " + chambers.size() + " chamber(s), " + empty + " ready")
            .description(sb.toString().trim())
            .primaryColor();
        return OK;
    }

    private int doDrop(com.mojang.brigadier.context.CommandContext<CommandContext> c, int x, int y, int z, int count) {
        boolean started = module().beginDepositNear(x, y, z, count);
        if (started) {
            c.getSource().getEmbed().title("PearlDrop")
                .description("Depositing " + count + " pearl(s) near " + x + ", " + y + ", " + z).successColor();
            return OK;
        }
        c.getSource().getEmbed().title("PearlDrop — nothing queued")
            .description("Result: " + module().lastResult().name()
                + " (no unoccupied chamber within tolerance of " + x + ", " + y + ", " + z + ")").errorColor();
        return ERROR;
    }

    private int doPick(com.mojang.brigadier.context.CommandContext<CommandContext> c, String raw) {
        List<StasisChamber> scan = module().getLastScan();
        if (scan.isEmpty()) {
            c.getSource().getEmbed().title("PearlDrop").description("No scan to pick from. Run /pd scan first.").errorColor();
            return ERROR;
        }
        List<StasisChamber> picked = new ArrayList<>();
        List<String> bad = new ArrayList<>();
        for (String tok : raw.trim().split("[,\\s]+")) {
            if (tok.isBlank()) continue;
            int idx;
            try { idx = Integer.parseInt(tok); } catch (NumberFormatException e) { bad.add(tok); continue; }
            if (idx < 1 || idx > scan.size()) { bad.add(tok); continue; }
            picked.add(scan.get(idx - 1));
        }
        if (picked.isEmpty()) {
            c.getSource().getEmbed().title("PearlDrop").description("No valid indices in: " + raw + " (scan has " + scan.size() + ").").errorColor();
            return ERROR;
        }
        int n = module().enqueueAll(picked, CONFIG.client.extra.pearlDrop.pearlsPerChamber);
        c.getSource().getEmbed().title("PearlDrop").description("Queued " + n + " chamber(s) for deposit"
            + (bad.isEmpty() ? "." : "; ignored invalid: " + String.join(", ", bad))).successColor();
        return OK;
    }
}
