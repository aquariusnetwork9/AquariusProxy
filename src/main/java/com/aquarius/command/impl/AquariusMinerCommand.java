package com.aquarius.command.impl;

import com.aquarius.module.impl.AquariusMiner;
import com.aquarius.module.impl.AquariusSnifferCodec;
import com.aquarius.module.impl.AquariusSniffer;
import com.aquarius.util.config.Config.Client.Extra.AquariusMiner.AreaAnchor;
import com.aquarius.util.config.Config.Client.Extra.AquariusMiner.AreaMode;
import com.aquarius.util.config.Config.Client.Extra.AquariusMiner.MineMode;
import com.aquarius.util.config.Config.Client.Extra.AquariusMiner.OreTool;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.Proxy;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.discord.Embed;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;
import static com.aquarius.Globals.CONFIG;

public class AquariusMinerCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("aquariusminer")
            .category(CommandCategory.MODULE)
            .aliases("aqm")
            .description("""
                AFK quarry miner. Clears one chunk at a time within a Y band, spiralling
                outward from where the bot is when enabled. Short alias: .aqm
                """)
            .usageLines(
                "on/off",
                "minY <y>",
                "maxY <y>",
                "here <length> <width>  (length forward, width right, from bot pos+facing)",
                "area unlimited",
                "area chunks <width> <length>",
                "area anchor <center/corner>",
                "area corners <x1> <z1> <x2> <z2>",
                "area loaded  (search the chunks loaded at start; stops when mined out)",
                "mode area/ore  (strip-mine vs tunnel to ore)",
                "tool fortune/silk  (ore: break for drops vs collect the block)",
                "ore add <block> | remove <block> | list | clear | reset",
                "autokeep on/off  (ore: derive the keep list from targets+tool)",
                "echestminy <y>  (lowest Y to place the field ender chest)",
                "keep add <item> | remove <item> | list | clear | reset",
                "cave on/off",
                "legit on/off  (break only blocks in line of sight)",
                "reach <blocks>  (legit reach; lower = closer, better pickup; ~4.5 vanilla)",
                "sprint on/off  (faster repositioning; off/on to apply)",
                "fullstacks on/off",
                "freeslots <n>  (store margin when full-stacks is off)",
                "dryrun on/off  (log echest shulkers + abort, no pull/store)",
                "badfood on/off",
                "autodc on/off",
                "pauseplayer on/off",
                "restock on/off",
                "shovel on/off",
                "food on/off | food count <n> | food min <n>",
                "clearbox <size>",
                "layer <blocks>",
                "verify on/off | verify retries <n>",
                "collect on/off | collect seconds <n>",
                "scan",
                "sniff on/off | dump | clear | 1s | 3s | 5s | 10s",
                "sniff live on/off | body on/off | dir in/out/both",
                "sniff filter <text>/off | template <name>/list/off",
                "deposit on/off",
                "deposit chest add <x> <y> <z> | clear",
                "deposit supply add <x> <y> <z> | clear",
                "deposit refill on/off | empties <n> | maxdist <blocks>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("aquariusminer")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.aquariusMiner.enabled = getToggle(c, "toggle");
                MODULE.get(AquariusMiner.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("Aquarius Miner " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.enabled));
            }))
            .then(literal("minY").then(argument("y", integer()).executes(c -> {
                int y = getInteger(c, "y");
                if (y > CONFIG.client.extra.aquariusMiner.maxY) {
                    c.getSource().getEmbed()
                        .title("Error")
                        .description("minY must be <= maxY (" + CONFIG.client.extra.aquariusMiner.maxY + ")");
                    return ERROR;
                }
                CONFIG.client.extra.aquariusMiner.minY = y;
                c.getSource().getEmbed().title("Min Y Set");
                return OK;
            })))
            .then(literal("maxY").then(argument("y", integer()).executes(c -> {
                int y = getInteger(c, "y");
                if (y < CONFIG.client.extra.aquariusMiner.minY) {
                    c.getSource().getEmbed()
                        .title("Error")
                        .description("maxY must be >= minY (" + CONFIG.client.extra.aquariusMiner.minY + ")");
                    return ERROR;
                }
                CONFIG.client.extra.aquariusMiner.maxY = y;
                c.getSource().getEmbed().title("Max Y Set");
                return OK;
            })))
            // Set the mining box from the bot's CURRENT position + facing: <length> chunks forward (the way
            // the bot looks, snapped to a cardinal) x <width> chunks to its right. Snapshots pos+yaw NOW into an
            // absolute Corners box, so it stays put even if the bot turns/walks later. Re-anchors live if running.
            .then(literal("here")
                .then(argument("length", integer(1))
                    .then(argument("width", integer(1)).executes(c -> {
                        if (!Proxy.getInstance().isConnected()) {
                            c.getSource().getEmbed().title("Error")
                                .description("Bot isn't connected - can't read its position/facing.");
                            return ERROR;
                        }
                        int len = getInteger(c, "length");
                        int wid = getInteger(c, "width");
                        var pc = CACHE.getPlayerCache();
                        int px = (int) Math.floor(pc.getX());
                        int pz = (int) Math.floor(pc.getZ());
                        double yaw = Math.toRadians(pc.getYaw());
                        double lx = -Math.sin(yaw), lz = Math.cos(yaw);
                        int fdx, fdz;                                  // forward unit vector, snapped to a cardinal
                        if (Math.abs(lx) >= Math.abs(lz)) { fdx = lx >= 0 ? 1 : -1; fdz = 0; }
                        else { fdx = 0; fdz = lz >= 0 ? 1 : -1; }
                        int rdx = -fdz, rdz = fdx;                     // right = forward rotated 90 deg clockwise
                        int cx0 = px >> 4, cz0 = pz >> 4;              // bot's chunk = the near corner
                        int cxB = cx0 + fdx * (len - 1) + rdx * (wid - 1);
                        int czB = cz0 + fdz * (len - 1) + rdz * (wid - 1);
                        int cxLo = Math.min(cx0, cxB), cxHi = Math.max(cx0, cxB);
                        int czLo = Math.min(cz0, czB), czHi = Math.max(cz0, czB);
                        var m = CONFIG.client.extra.aquariusMiner;
                        m.areaMode = AreaMode.Corners;
                        m.corner1X = cxLo << 4;          m.corner1Z = czLo << 4;
                        m.corner2X = (cxHi << 4) + 15;   m.corner2Z = (czHi << 4) + 15;
                        boolean live = m.enabled;
                        if (live) MODULE.get(AquariusMiner.class).requestReanchor();
                        c.getSource().getEmbed()
                            .title("Area: " + len + " x " + wid + " chunks from here (" + (len * wid) + " total)")
                            .description(len + " forward (" + cardinal(fdx, fdz) + "), "
                                + wid + " right (" + cardinal(rdx, rdz) + ")\n"
                                + "X[" + (cxLo << 4) + ".." + ((cxHi << 4) + 15) + "] "
                                + "Z[" + (czLo << 4) + ".." + ((czHi << 4) + 15) + "], Y "
                                + m.minY + ".." + m.maxY + "\n"
                                + (live ? "Re-anchored live - mining the new box now." : "Run `.aqm on` to start."));
                        return OK;
                    }))))
            .then(literal("keep")
                .then(literal("add").then(argument("item", word()).executes(c -> {
                    String item = getString(c, "item").toLowerCase();
                    if (CONFIG.client.extra.aquariusMiner.keepItems.contains(item)) {
                        c.getSource().getEmbed().title("Already kept: " + item);
                    } else {
                        CONFIG.client.extra.aquariusMiner.keepItems.add(item);
                        c.getSource().getEmbed().title("Keeping " + item)
                            .description("Now keeping: " + String.join(", ", CONFIG.client.extra.aquariusMiner.keepItems));
                    }
                })))
                .then(literal("remove").then(argument("item", word()).executes(c -> {
                    String item = getString(c, "item").toLowerCase();
                    boolean removed = CONFIG.client.extra.aquariusMiner.keepItems.remove(item);
                    c.getSource().getEmbed().title(removed ? "Stopped keeping " + item : "Not in keep list: " + item)
                        .description("Now keeping: " + String.join(", ", CONFIG.client.extra.aquariusMiner.keepItems));
                })))
                .then(literal("list").executes(c -> {
                    var keep = CONFIG.client.extra.aquariusMiner.keepItems;
                    c.getSource().getEmbed().title("Keep items (" + keep.size() + ")")
                        .description(keep.isEmpty() ? "(none)" : String.join(", ", keep));
                }))
                .then(literal("clear").executes(c -> {
                    CONFIG.client.extra.aquariusMiner.keepItems.clear();
                    c.getSource().getEmbed().title("Keep list cleared")
                        .description("Nothing will be deposited - add ores/blocks with `.aqm keep add <item>`.");
                }))
                .then(literal("reset").executes(c -> {
                    CONFIG.client.extra.aquariusMiner.keepItems.clear();
                    CONFIG.client.extra.aquariusMiner.keepItems.add("deepslate");
                    CONFIG.client.extra.aquariusMiner.keepItems.add("cobbled_deepslate");
                    c.getSource().getEmbed().title("Keep list reset")
                        .description("Now keeping: " + String.join(", ", CONFIG.client.extra.aquariusMiner.keepItems));
                })))
            .then(literal("area")
                .then(literal("unlimited").executes(c -> {
                    CONFIG.client.extra.aquariusMiner.areaMode = AreaMode.Unlimited;
                    c.getSource().getEmbed().title("Area: Unlimited");
                }))
                .then(literal("chunks")
                    .then(argument("width", integer(1))
                        .then(argument("length", integer(1)).executes(c -> {
                            CONFIG.client.extra.aquariusMiner.areaMode = AreaMode.ChunksFromStart;
                            CONFIG.client.extra.aquariusMiner.areaWidthChunks = getInteger(c, "width");
                            CONFIG.client.extra.aquariusMiner.areaLengthChunks = getInteger(c, "length");
                            c.getSource().getEmbed().title("Area: " + getInteger(c, "width")
                                + " x " + getInteger(c, "length") + " chunks from start");
                        }))))
                .then(literal("anchor")
                    .then(literal("center").executes(c -> {
                        CONFIG.client.extra.aquariusMiner.areaAnchor = AreaAnchor.Center;
                        c.getSource().getEmbed().title("Area anchor: Center");
                    }))
                    .then(literal("corner").executes(c -> {
                        CONFIG.client.extra.aquariusMiner.areaAnchor = AreaAnchor.Corner;
                        c.getSource().getEmbed().title("Area anchor: Corner");
                    })))
                .then(literal("loaded").executes(c -> {
                    CONFIG.client.extra.aquariusMiner.areaMode = AreaMode.LoadedAtStart;
                    boolean live = CONFIG.client.extra.aquariusMiner.enabled;
                    if (live) MODULE.get(AquariusMiner.class).requestReanchor();
                    c.getSource().getEmbed()
                        .title("Area: loaded chunks at start")
                        .description("When the run starts it captures the render-distance box around the bot and "
                            + "locks it to an absolute box (survives a reconnect). The run finishes once those "
                            + "chunks are mined out (also stops on no storage / death)."
                            + (live ? "\nRe-anchored live." : "\nRun `.aqm on` to start."));
                }))
                .then(literal("corners")
                    .then(argument("x1", integer())
                        .then(argument("z1", integer())
                            .then(argument("x2", integer())
                                .then(argument("z2", integer()).executes(c -> {
                                    var m = CONFIG.client.extra.aquariusMiner;
                                    m.areaMode = AreaMode.Corners;
                                    m.corner1X = getInteger(c, "x1");
                                    m.corner1Z = getInteger(c, "z1");
                                    m.corner2X = getInteger(c, "x2");
                                    m.corner2Z = getInteger(c, "z2");
                                    c.getSource().getEmbed().title("Area: corners ("
                                        + m.corner1X + ", " + m.corner1Z + ") -> ("
                                        + m.corner2X + ", " + m.corner2Z + ")");
                                })))))))
            .then(literal("mode")
                .then(literal("area").executes(c -> {
                    CONFIG.client.extra.aquariusMiner.mineMode = MineMode.AreaClear;
                    c.getSource().getEmbed().title("Mode: AreaClear")
                        .description("Strip-mines every block in the area, chunk by chunk (the original behaviour).");
                }))
                .then(literal("ore").executes(c -> {
                    CONFIG.client.extra.aquariusMiner.mineMode = MineMode.OreSearch;
                    MODULE.get(AquariusMiner.class).syncOreConfig();
                    c.getSource().getEmbed().title("Mode: OreSearch")
                        .description("Searches the area for the configured ore and tunnels to it - no strip-mining. "
                            + "Set targets with `.aqm ore`, tool with `.aqm tool`. Bound it with `.aqm area` (or it "
                            + "searches unlimited).");
                })))
            .then(literal("tool")
                .then(literal("fortune").executes(c -> {
                    CONFIG.client.extra.aquariusMiner.oreTool = OreTool.Fortune;
                    MODULE.get(AquariusMiner.class).syncOreConfig();
                    c.getSource().getEmbed().title("Ore tool: Fortune")
                        .description("Breaks ore with a non-silk pickaxe and keeps the DROP (raw_iron, diamond, ...). "
                            + "Give the bot a fortune pickaxe for more drops.");
                }))
                .then(literal("silk").executes(c -> {
                    CONFIG.client.extra.aquariusMiner.oreTool = OreTool.SilkTouch;
                    MODULE.get(AquariusMiner.class).syncOreConfig();
                    c.getSource().getEmbed().title("Ore tool: Silk Touch")
                        .description("Breaks ore with a silk-touch pickaxe and keeps the ORE BLOCK itself "
                            + "(deepslate_diamond_ore, ...). Keep a silk pickaxe on the bot.");
                })))
            .then(literal("autokeep").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.aquariusMiner.oreAutoKeep = getToggle(c, "toggle");
                MODULE.get(AquariusMiner.class).syncOreConfig();
                c.getSource().getEmbed()
                    .title("Ore auto-keep " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.oreAutoKeep))
                    .description(CONFIG.client.extra.aquariusMiner.oreAutoKeep
                        ? "OreSearch derives the haul list from the targets + tool automatically."
                        : "OreSearch uses the explicit `.aqm keep` list - manage it yourself.");
            })))
            .then(literal("echestminy").then(argument("y", integer()).executes(c -> {
                CONFIG.client.extra.aquariusMiner.minEchestY = getInteger(c, "y");
                c.getSource().getEmbed()
                    .title("Echest min-Y set to " + CONFIG.client.extra.aquariusMiner.minEchestY)
                    .description("Storage prefers placing the field ender chest at/above this Y (keeps it off the "
                        + "bedrock floor). Best-effort: falls back to the best spot when the bot is mining deeper.");
            })))
            .then(literal("ore")
                .then(literal("add").then(argument("block", word()).executes(c -> {
                    String b = getString(c, "block").toLowerCase();
                    var t = CONFIG.client.extra.aquariusMiner.oreTargets;
                    if (t.contains(b)) {
                        c.getSource().getEmbed().title("Already a target: " + b);
                    } else {
                        t.add(b);
                        MODULE.get(AquariusMiner.class).syncOreConfig();
                        c.getSource().getEmbed().title("Ore target added: " + b)
                            .description("Targets: " + String.join(", ", t));
                    }
                })))
                .then(literal("remove").then(argument("block", word()).executes(c -> {
                    String b = getString(c, "block").toLowerCase();
                    boolean removed = CONFIG.client.extra.aquariusMiner.oreTargets.remove(b);
                    MODULE.get(AquariusMiner.class).syncOreConfig();
                    c.getSource().getEmbed().title(removed ? "Removed target: " + b : "Not a target: " + b)
                        .description("Targets: " + String.join(", ", CONFIG.client.extra.aquariusMiner.oreTargets));
                })))
                .then(literal("list").executes(c -> {
                    var t = CONFIG.client.extra.aquariusMiner.oreTargets;
                    c.getSource().getEmbed().title("Ore targets (" + t.size() + ")")
                        .description(t.isEmpty() ? "(none)" : String.join(", ", t));
                }))
                .then(literal("clear").executes(c -> {
                    CONFIG.client.extra.aquariusMiner.oreTargets.clear();
                    MODULE.get(AquariusMiner.class).syncOreConfig();
                    c.getSource().getEmbed().title("Ore targets cleared")
                        .description("Add ores with `.aqm ore add <block>` or `.aqm ore reset` for all ores.");
                }))
                .then(literal("reset").executes(c -> {
                    var t = CONFIG.client.extra.aquariusMiner.oreTargets;
                    t.clear();
                    t.addAll(java.util.List.of(
                        "coal_ore", "deepslate_coal_ore", "iron_ore", "deepslate_iron_ore",
                        "copper_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore",
                        "redstone_ore", "deepslate_redstone_ore", "lapis_ore", "deepslate_lapis_ore",
                        "diamond_ore", "deepslate_diamond_ore", "emerald_ore", "deepslate_emerald_ore",
                        "nether_gold_ore", "nether_quartz_ore", "ancient_debris"));
                    MODULE.get(AquariusMiner.class).syncOreConfig();
                    c.getSource().getEmbed().title("Ore targets reset (all ores)")
                        .description("Targets: " + String.join(", ", t));
                })))
            .then(literal("cave").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.aquariusMiner.caveHandling = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Cave handling " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.caveHandling));
            })))
            .then(literal("legit").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.aquariusMiner.legitMine = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Legit mining " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.legitMine))
                    .description(CONFIG.client.extra.aquariusMiner.legitMine
                        ? "Breaks only blocks the bot can see (no reaching through walls)."
                        : "Fast engine - may reach through walls to occluded blocks.");
            })))
            .then(literal("reach").then(argument("blocks", doubleArg(1.0, 6.0)).executes(c -> {
                CONFIG.client.extra.aquariusMiner.miningReach = getDouble(c, "blocks");
                c.getSource().getEmbed()
                    .title("Mining reach " + String.format("%.1f", CONFIG.client.extra.aquariusMiner.miningReach))
                    .description("Lower keeps the bot close to what it mines (drops land next to it -> picked up better); higher reaches further (drops scatter). Vanilla is ~4.5. Live - applies on the next block.");
            })))
            .then(literal("sprint").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.aquariusMiner.sprint = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Sprint " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.sprint))
                    .description("Faster repositioning, sub-box hops, and drop chasing. Toggle the miner off/on to apply (it's pushed when the module enables).");
            })))
            .then(literal("fullstacks").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.aquariusMiner.requireFullStacks = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Require full stacks " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.requireFullStacks));
            })))
            .then(literal("freeslots").then(argument("n", integer(0)).executes(c -> {
                CONFIG.client.extra.aquariusMiner.freeSlotsBeforeFull = getInteger(c, "n");
                c.getSource().getEmbed()
                    .title("Free-slots-before-full set to " + CONFIG.client.extra.aquariusMiner.freeSlotsBeforeFull)
                    .description("Used only when full-stacks is OFF: store when this many empty main slots remain.");
            })))
            .then(literal("dryrun").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.aquariusMiner.dryRunStorage = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Storage dry-run " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.dryRunStorage))
                    .description(CONFIG.client.extra.aquariusMiner.dryRunStorage
                        ? "Next store will LOG the echest's shulkers (name + contents + empty/full verdict) then abort WITHOUT pulling or storing anything."
                        : "Storage runs normally.");
            })))
            .then(literal("badfood").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.aquariusMiner.dropBadFood = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Drop bad food " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.dropBadFood));
            })))
            .then(literal("autodc").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.aquariusMiner.autoDisconnect = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Auto-disconnect " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.autoDisconnect));
            })))
            .then(literal("pauseplayer").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.aquariusMiner.pauseOnPlayer = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Pause on player " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.pauseOnPlayer));
            })))
            .then(literal("restock").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.aquariusMiner.restockTools = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Tool restock " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.restockTools));
            })))
            .then(literal("clearbox").then(argument("size", integer(1)).executes(c -> {
                CONFIG.client.extra.aquariusMiner.clearBoxSize = getInteger(c, "size");
                c.getSource().getEmbed().title("Clear-box size: " + getInteger(c, "size")
                    + " (smaller = better drop collection)");
            })))
            .then(literal("layer").then(argument("blocks", integer(1)).executes(c -> {
                CONFIG.client.extra.aquariusMiner.layerHeight = getInteger(c, "blocks");
                c.getSource().getEmbed().title("Layer height: " + getInteger(c, "blocks")
                    + " (top-down; 1 = peel one level across the whole area before descending)");
            })))
            .then(literal("collect")
                .then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.aquariusMiner.collectDrops = getToggle(c, "toggle");
                    c.getSource().getEmbed().title("Collect drops " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.collectDrops));
                }))
                .then(literal("seconds").then(argument("n", integer(1)).executes(c -> {
                    CONFIG.client.extra.aquariusMiner.collectMaxSeconds = getInteger(c, "n");
                    c.getSource().getEmbed().title("Collect cap: " + getInteger(c, "n") + "s per sub-box");
                }))))
            .then(literal("shovel").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.aquariusMiner.alsoRestockShovel = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Also restock shovel " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.alsoRestockShovel));
            })))
            .then(literal("food")
                .then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.aquariusMiner.restockFood = getToggle(c, "toggle");
                    c.getSource().getEmbed().title("Food restock " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.restockFood));
                }))
                .then(literal("count").then(argument("n", integer(1)).executes(c -> {
                    CONFIG.client.extra.aquariusMiner.foodRestockCount = getInteger(c, "n");
                    c.getSource().getEmbed().title("Food restock target: " + getInteger(c, "n"));
                })))
                .then(literal("min").then(argument("n", integer(1)).executes(c -> {
                    CONFIG.client.extra.aquariusMiner.minFoodOnHand = getInteger(c, "n");
                    c.getSource().getEmbed().title("Restock food when carried food < " + getInteger(c, "n"));
                }))))
            .then(literal("verify")
                .then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.aquariusMiner.verifyClears = getToggle(c, "toggle");
                    c.getSource().getEmbed().title("Verify clears " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.verifyClears));
                }))
                .then(literal("retries").then(argument("n", integer(0)).executes(c -> {
                    CONFIG.client.extra.aquariusMiner.clearRetries = getInteger(c, "n");
                    c.getSource().getEmbed().title("Sub-box clear retries: " + getInteger(c, "n"));
                }))))
            .then(literal("scan").executes(c -> {
                MODULE.get(AquariusMiner.class).printScan();
                c.getSource().getEmbed().title("Resource scan printed to console + in-game alert");
            }))
            .then(literal("sniff")
                .then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.aquariusMiner.sniffEnabled = getToggle(c, "toggle");
                    MODULE.get(AquariusSniffer.class).syncEnabledFromConfig();
                    c.getSource().getEmbed()
                        .title("Packet sniffer " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.sniffEnabled))
                        .description("Captures bot<->server packets into a rolling "
                            + CONFIG.client.extra.aquariusMiner.sniffBufferLines + "-line buffer. `.aqm sniff dump` to print it.");
                }))
                .then(literal("dump").executes(c -> {
                    var m = MODULE.get(AquariusSniffer.class);
                    m.dump();
                    c.getSource().getEmbed().title("Sniffer dump")
                        .description("Printed " + m.bufferSize() + " buffered packet lines to the console.");
                }))
                .then(literal("clear").executes(c -> {
                    MODULE.get(AquariusSniffer.class).clearBuffer();
                    c.getSource().getEmbed().title("Sniffer buffer cleared");
                }))
                .then(literal("1s").executes(c -> { MODULE.get(AquariusSniffer.class).startTimed(1);
                    c.getSource().getEmbed().title("Capturing packets for 1s (verbose) - then auto-dump"); }))
                .then(literal("3s").executes(c -> { MODULE.get(AquariusSniffer.class).startTimed(3);
                    c.getSource().getEmbed().title("Capturing packets for 3s (verbose) - then auto-dump"); }))
                .then(literal("5s").executes(c -> { MODULE.get(AquariusSniffer.class).startTimed(5);
                    c.getSource().getEmbed().title("Capturing packets for 5s (verbose) - then auto-dump"); }))
                .then(literal("10s").executes(c -> { MODULE.get(AquariusSniffer.class).startTimed(10);
                    c.getSource().getEmbed().title("Capturing packets for 10s (verbose) - then auto-dump"); }))
                .then(literal("live").then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.aquariusMiner.sniffLive = getToggle(c, "toggle");
                    c.getSource().getEmbed().title("Sniffer live logging " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.sniffLive));
                })))
                .then(literal("body").then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.aquariusMiner.sniffBody = getToggle(c, "toggle");
                    c.getSource().getEmbed().title("Sniffer packet body " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.sniffBody));
                })))
                .then(literal("dir")
                    .then(literal("in").executes(c -> { CONFIG.client.extra.aquariusMiner.sniffDir = "in";
                        c.getSource().getEmbed().title("Sniffer direction: inbound (from server) only"); }))
                    .then(literal("out").executes(c -> { CONFIG.client.extra.aquariusMiner.sniffDir = "out";
                        c.getSource().getEmbed().title("Sniffer direction: outbound (to server) only"); }))
                    .then(literal("both").executes(c -> { CONFIG.client.extra.aquariusMiner.sniffDir = "both";
                        c.getSource().getEmbed().title("Sniffer direction: both"); })))
                .then(literal("filter")
                    .then(literal("off").executes(c -> { CONFIG.client.extra.aquariusMiner.sniffFilter = "";
                        c.getSource().getEmbed().title("Sniffer filter cleared"); }))
                    .then(argument("text", word()).executes(c -> {
                        CONFIG.client.extra.aquariusMiner.sniffFilter = getString(c, "text");
                        c.getSource().getEmbed().title("Sniffer filter: " + getString(c, "text"))
                            .description("Only packets whose class name contains this are captured.");
                    })))
                .then(literal("template")
                    .then(literal("list").executes(c -> {
                        c.getSource().getEmbed().title("Sniffer templates")
                            .description(String.join(", ", AquariusSnifferCodec.templateNames())
                                + "\nApply with `.aqm sniff template <name>`, clear with `.aqm sniff template off`.");
                    }))
                    .then(literal("off").executes(c -> { CONFIG.client.extra.aquariusMiner.sniffTemplate = "";
                        c.getSource().getEmbed().title("Sniffer template cleared"); }))
                    .then(argument("name", word()).executes(c -> {
                        String resolved = AquariusSnifferCodec.resolveTemplate(getString(c, "name"));
                        if (resolved == null) {
                            c.getSource().getEmbed().title("Unknown template: " + getString(c, "name"))
                                .description("Options: " + String.join(", ", AquariusSnifferCodec.templateNames()));
                            return ERROR;
                        }
                        CONFIG.client.extra.aquariusMiner.sniffTemplate = resolved;
                        c.getSource().getEmbed().title("Sniffer template: " + resolved)
                            .description("Matches names containing: " + String.join(", ", AquariusSnifferCodec.templateSubs(resolved)));
                        return OK;
                    }))))
            .then(literal("deposit")
                .then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.aquariusMiner.depositToChests = getToggle(c, "toggle");
                    c.getSource().getEmbed()
                        .title("Deposit to chests " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.depositToChests));
                }))
                .then(literal("chest")
                    .then(literal("add").then(argument("x", integer()).then(argument("y", integer()).then(argument("z", integer()).executes(c -> {
                        CONFIG.client.extra.aquariusMiner.depositChests.add(getInteger(c, "x") + " " + getInteger(c, "y") + " " + getInteger(c, "z"));
                        c.getSource().getEmbed().title("Deposit chest added (" + CONFIG.client.extra.aquariusMiner.depositChests.size() + " total)");
                    })))))
                    .then(literal("clear").executes(c -> {
                        CONFIG.client.extra.aquariusMiner.depositChests.clear();
                        c.getSource().getEmbed().title("Deposit chests cleared");
                    })))
                .then(literal("supply")
                    .then(literal("add").then(argument("x", integer()).then(argument("y", integer()).then(argument("z", integer()).executes(c -> {
                        CONFIG.client.extra.aquariusMiner.supplyChests.add(getInteger(c, "x") + " " + getInteger(c, "y") + " " + getInteger(c, "z"));
                        c.getSource().getEmbed().title("Supply chest added (" + CONFIG.client.extra.aquariusMiner.supplyChests.size() + " total)");
                    })))))
                    .then(literal("clear").executes(c -> {
                        CONFIG.client.extra.aquariusMiner.supplyChests.clear();
                        c.getSource().getEmbed().title("Supply chests cleared");
                    })))
                .then(literal("refill").then(argument("toggle", toggle()).executes(c -> {
                    CONFIG.client.extra.aquariusMiner.refillEmpties = getToggle(c, "toggle");
                    c.getSource().getEmbed().title("Refill empties " + toggleStrCaps(CONFIG.client.extra.aquariusMiner.refillEmpties));
                })))
                .then(literal("empties").then(argument("n", integer(1)).executes(c -> {
                    CONFIG.client.extra.aquariusMiner.emptiesPerTrip = getInteger(c, "n");
                    c.getSource().getEmbed().title("Empties per trip: " + getInteger(c, "n"));
                })))
                .then(literal("maxdist").then(argument("blocks", integer(0)).executes(c -> {
                    CONFIG.client.extra.aquariusMiner.maxDepositDistance = getInteger(c, "blocks");
                    c.getSource().getEmbed().title("Max deposit distance: " + getInteger(c, "blocks") + " blocks");
                }))));
    }

    /** Cardinal name for a unit chunk-direction (one axis zero): -Z north, +Z south, +X east, -X west. */
    private static String cardinal(int dx, int dz) {
        if (dz < 0) return "north";
        if (dz > 0) return "south";
        if (dx > 0) return "east";
        if (dx < 0) return "west";
        return "?";
    }

    private static String areaDescription() {
        var m = CONFIG.client.extra.aquariusMiner;
        return switch (m.areaMode) {
            case Unlimited -> "Unlimited";
            case ChunksFromStart -> m.areaWidthChunks + " x " + m.areaLengthChunks + " chunks (" + m.areaAnchor + ")";
            case Corners -> "(" + m.corner1X + ", " + m.corner1Z + ") -> (" + m.corner2X + ", " + m.corner2Z + ")";
            case LoadedAtStart -> "loaded chunks at start (captured on run)";
        };
    }

    @Override
    public void defaultEmbed(Embed embed) {
        var module = MODULE.get(AquariusMiner.class);
        embed
            .primaryColor()
            .addField("Enabled", toggleStr(CONFIG.client.extra.aquariusMiner.enabled))
            .addField("State", module.statusLine())
            .addField("Y Band", CONFIG.client.extra.aquariusMiner.minY + " .. " + CONFIG.client.extra.aquariusMiner.maxY)
            .addField("Area", areaDescription())
            .addField("Mode", CONFIG.client.extra.aquariusMiner.mineMode == MineMode.OreSearch
                ? "OreSearch (" + CONFIG.client.extra.aquariusMiner.oreTool + ", "
                    + CONFIG.client.extra.aquariusMiner.oreTargets.size() + " ore types, "
                    + (CONFIG.client.extra.aquariusMiner.oreAutoKeep ? "auto-keep" : "manual keep") + ")"
                : "AreaClear (strip-mine)")
            .addField("Keep", (CONFIG.client.extra.aquariusMiner.mineMode == MineMode.OreSearch
                    && CONFIG.client.extra.aquariusMiner.oreAutoKeep)
                ? String.join(", ", module.oreKeepNames())
                : String.join(", ", CONFIG.client.extra.aquariusMiner.keepItems))
            .addField("Drop Junk", toggleStr(CONFIG.client.extra.aquariusMiner.dropJunk)
                + (CONFIG.client.extra.aquariusMiner.dropBadFood ? " (+bad food)" : ""))
            .addField("Cave Handling", toggleStr(CONFIG.client.extra.aquariusMiner.caveHandling)
                + (CONFIG.client.extra.aquariusMiner.caveHandling ? " (fall " + CONFIG.client.extra.aquariusMiner.maxFallHeight + ")" : ""))
            .addField("Mining", (CONFIG.client.extra.aquariusMiner.legitMine ? "legit (line of sight)" : "fast (can reach through walls)")
                + ", reach " + String.format("%.1f", CONFIG.client.extra.aquariusMiner.miningReach)
                + ", sprint " + toggleStr(CONFIG.client.extra.aquariusMiner.sprint))
            .addField("Storage", toggleStr(CONFIG.client.extra.aquariusMiner.storageEnabled)
                + (CONFIG.client.extra.aquariusMiner.requireFullStacks ? " (full stacks)" : " (margin " + CONFIG.client.extra.aquariusMiner.freeSlotsBeforeFull + ")")
                + (CONFIG.client.extra.aquariusMiner.breakAndCollect ? ", break & collect" : ", leave")
                + ", echest min-Y " + CONFIG.client.extra.aquariusMiner.minEchestY)
            .addField("Restock", CONFIG.client.extra.aquariusMiner.restockTools
                ? "on (" + CONFIG.client.extra.aquariusMiner.restockToolKeyword + " < " + CONFIG.client.extra.aquariusMiner.restockBelowDurability + ")"
                    + (CONFIG.client.extra.aquariusMiner.alsoRestockShovel ? " +shovel" : "")
                : "off")
            .addField("Food", CONFIG.client.extra.aquariusMiner.restockFood
                ? "on (restock to " + CONFIG.client.extra.aquariusMiner.foodRestockCount + " when < " + CONFIG.client.extra.aquariusMiner.minFoodOnHand + ")"
                : "off")
            .addField("Collection", "clear-box " + CONFIG.client.extra.aquariusMiner.clearBoxSize
                + ", layer " + CONFIG.client.extra.aquariusMiner.layerHeight
                + ", vacuum " + toggleStr(CONFIG.client.extra.aquariusMiner.collectDrops)
                + ", verify " + (CONFIG.client.extra.aquariusMiner.verifyClears ? "on (" + CONFIG.client.extra.aquariusMiner.clearRetries + " retries)" : "off"))
            .addField("Deposit", CONFIG.client.extra.aquariusMiner.depositToChests
                ? "on (" + CONFIG.client.extra.aquariusMiner.depositChests.size() + " deposit, "
                    + CONFIG.client.extra.aquariusMiner.supplyChests.size() + " supply"
                    + (CONFIG.client.extra.aquariusMiner.refillEmpties ? "; refill " + CONFIG.client.extra.aquariusMiner.emptiesPerTrip : "") + ")"
                : "off")
            .addField("Safety", "player-pause " + toggleStr(CONFIG.client.extra.aquariusMiner.pauseOnPlayer)
                + " (" + (int) CONFIG.client.extra.aquariusMiner.playerPauseRange + "b), auto-dc " + toggleStr(CONFIG.client.extra.aquariusMiner.autoDisconnect))
            .addField("Sniffer", CONFIG.client.extra.aquariusMiner.sniffEnabled
                ? "on (" + CONFIG.client.extra.aquariusMiner.sniffDir
                    + (CONFIG.client.extra.aquariusMiner.sniffTemplate.isEmpty() ? "" : ", template " + CONFIG.client.extra.aquariusMiner.sniffTemplate)
                    + (CONFIG.client.extra.aquariusMiner.sniffFilter.isEmpty() ? "" : ", filter '" + CONFIG.client.extra.aquariusMiner.sniffFilter + "'")
                    + (MODULE.get(AquariusSniffer.class).bufferSize() > 0 ? ", " + MODULE.get(AquariusSniffer.class).bufferSize() + " buffered" : "") + ")"
                : "off");
    }
}
