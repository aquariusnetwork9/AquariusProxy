package com.aquarius.command.impl;

import com.aquarius.module.impl.Regear;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import static com.aquarius.Globals.DISCORD;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;
import static com.aquarius.Globals.CONFIG;
import com.aquarius.discord.Panels;

public class RegearCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("regear")
            .category(CommandCategory.MODULE)
            .aliases("rg")
            .description("""
                Resupply from a kit shulker in an ender chest: place/open the echest, pull the named
                kit shulker, empty it, return it, then gear up. One-shot. Short alias: .rg
                """)
            .usageLines(
                "on/off",
                "name <text>  (custom name of the kit shulker)",
                "color <name>/off  (match the kit shulker by colour instead)",
                "contents on/off  (match the kit by its contents: elytra+fireworks+kit items, any name/colour)",
                "scanradius <n>  (fallback: find a placed echest within n blocks)",
                "armor on/off  (equip the kit's armor on finish)",
                "totem on/off  (put a totem in the offhand on finish)",
                "return on/off  (return the emptied shulker to the echest)",
                "pauseplayer on/off",
                "once on/off  (toggle the module off after a successful regear)",
                "ghost on/off  (open containers through walls/no-LOS within ghostreach)",
                "ghostreach <n>  (max blocks for a ghost-hand open; 2b2t tolerates ~6)",
                "relocate on/off  (self-kill to respawn until open sky + a reachable echest)",
                "skyclearance <n>  (air blocks above head required to count as open sky)",
                "relocateattempts <n>  (max self-kills before giving up)",
                "panel  (post an interactive Regear panel to Discord: match-mode dropdown + toggles + kit/threshold modals + run)"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("regear")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.enabled = getToggle(c, "toggle");
                MODULE.get(Regear.class).syncEnabledFromConfig();
                c.getSource().getEmbed().title("Regear " + toggleStrCaps(CONFIG.client.extra.regear.enabled));
            }))
            .then(literal("name").then(argument("text", word()).executes(c -> {
                CONFIG.client.extra.regear.kitShulkerName = getString(c, "text");
                CONFIG.client.extra.regear.matchByColor = false;
                CONFIG.client.extra.regear.matchByContents = false;
                c.getSource().getEmbed().title("Kit shulker name: " + getString(c, "text"))
                    .description("Matches a shulker whose custom name contains this (case-insensitive).");
            })))
            .then(literal("color")
                .then(literal("off").executes(c -> {
                    CONFIG.client.extra.regear.matchByColor = false;
                    c.getSource().getEmbed().title("Kit shulker matched by name");
                }))
                .then(argument("name", word()).executes(c -> {
                    CONFIG.client.extra.regear.kitShulkerColor = getString(c, "name").toLowerCase();
                    CONFIG.client.extra.regear.matchByColor = true;
                    CONFIG.client.extra.regear.matchByContents = false;
                    c.getSource().getEmbed().title("Kit shulker color: " + getString(c, "name"))
                        .description("Matches a " + getString(c, "name") + "_shulker_box.");
                })))
            .then(literal("contents").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.matchByContents = getToggle(c, "toggle");
                if (CONFIG.client.extra.regear.matchByContents) CONFIG.client.extra.regear.matchByColor = false;
                c.getSource().getEmbed().title("Match kit by contents " + toggleStrCaps(CONFIG.client.extra.regear.matchByContents))
                    .description("Identifies the kit shulker by what's inside it (elytra + fireworks required), ignoring name/colour. Picks the most complete flight kit.");
            })))
            .then(literal("profile")
                .then(literal("off").executes(c -> {
                    CONFIG.client.extra.regear.profile = "";
                    c.getSource().getEmbed().title("Regear kit profile: off (legacy name/colour/contents fields)");
                }))
                .then(argument("name", word()).executes(c -> {
                    String name = getString(c, "name");
                    boolean known = CONFIG.client.extra.kitProfile(name) != null;
                    CONFIG.client.extra.regear.profile = name;
                    c.getSource().getEmbed().title("Regear kit profile: " + name + (known ? "" : "  (no such profile yet — add it with `kit add " + name + "`)"))
                        .description("Spawn/combat regear pulls this kit profile (a name in `kit list`). Use `profile off` for the legacy fields.");
                })))
            .then(literal("scanradius").then(argument("n", integer(1)).executes(c -> {
                CONFIG.client.extra.regear.echestScanRadius = getInteger(c, "n");
                c.getSource().getEmbed().title("Echest fallback scan radius: " + getInteger(c, "n") + " blocks");
            })))
            .then(literal("armor").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.equipArmor = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Equip armor " + toggleStrCaps(CONFIG.client.extra.regear.equipArmor));
            })))
            .then(literal("elytra").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.equipElytra = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Equip elytra (chest slot) " + toggleStrCaps(CONFIG.client.extra.regear.equipElytra))
                    .description("Equips an elytra into the chest slot instead of a chestplate (for flight). The ElytraTrip pre-flight gear-up forces this on automatically.");
            })))
            .then(literal("totem").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.offhandTotem = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Offhand totem " + toggleStrCaps(CONFIG.client.extra.regear.offhandTotem));
            })))
            .then(literal("return").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.returnShulker = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Return emptied shulker " + toggleStrCaps(CONFIG.client.extra.regear.returnShulker));
            })))
            .then(literal("pauseplayer").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.pauseOnPlayer = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Pause on player " + toggleStrCaps(CONFIG.client.extra.regear.pauseOnPlayer));
            })))
            .then(literal("once").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.disableWhenDone = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Disable-when-done " + toggleStrCaps(CONFIG.client.extra.regear.disableWhenDone));
            })))
            .then(literal("ghost").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.ghostInteract = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Ghost-hand open " + toggleStrCaps(CONFIG.client.extra.regear.ghostInteract))
                    .description("Open containers via a direct interaction packet (no line-of-sight) once within ghostreach — opens chests through walls.");
            })))
            .then(literal("ghostreach").then(argument("n", doubleArg(1)).executes(c -> {
                CONFIG.client.extra.regear.ghostReach = getDouble(c, "n");
                c.getSource().getEmbed().title("Ghost-hand reach: " + CONFIG.client.extra.regear.ghostReach + " blocks (2b2t tolerates ~6)");
            })))
            .then(literal("relocate").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.regear.selfKillRelocate = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Self-kill relocate " + toggleStrCaps(CONFIG.client.extra.regear.selfKillRelocate))
                    .description("If boxed-in / no reachable echest, /kill and let AutoRespawn relocate the bot; repeat until open sky + a reachable echest. Needs AutoRespawn.");
            })))
            .then(literal("skyclearance").then(argument("n", integer(1)).executes(c -> {
                CONFIG.client.extra.regear.relocateMinSkyClearance = getInteger(c, "n");
                c.getSource().getEmbed().title("Relocate sky clearance: " + getInteger(c, "n") + " air blocks above the head");
            })))
            .then(literal("relocateattempts").then(argument("n", integer(1)).executes(c -> {
                CONFIG.client.extra.regear.relocateMaxAttempts = getInteger(c, "n");
                c.getSource().getEmbed().title("Relocate max self-kills: " + getInteger(c, "n"));
            })))
            .then(literal("panel").executes(c -> {
                boolean posted = DISCORD.openPanel(Panels.REGEAR);
                c.getSource().getEmbed()
                    .title(posted ? "Regear panel posted to Discord" : "Discord bot not running")
                    .description(posted
                        ? "In Discord: pick the match mode (dropdown), toggle gear-up/safety options, Set kit (name/colour) or Thresholds (modals), then Run regear."
                        : "Enable the Discord bot to use the interactive regear panel.");
            }));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        var cfg = CONFIG.client.extra.regear;
        var module = MODULE.get(Regear.class);
        embed
            .primaryColor()
            .addField("Enabled", toggleStr(cfg.enabled))
            .addField("State", module.statusLine())
            .addField("Kit shulker", cfg.matchByContents ? "by contents (elytra+fireworks)"
                : cfg.matchByColor ? "color: " + cfg.kitShulkerColor : "name: " + cfg.kitShulkerName)
            .addField("Echest", "place own (fallback scan " + cfg.echestScanRadius + "b)")
            .addField("Gear up", "armor " + toggleStr(cfg.equipArmor) + ", totem " + toggleStr(cfg.offhandTotem))
            .addField("Return shulker", toggleStr(cfg.returnShulker))
            .addField("Safety", "player-pause " + toggleStr(cfg.pauseOnPlayer) + " (" + (int) cfg.playerPauseRange + "b)")
            .addField("One-shot", toggleStr(cfg.disableWhenDone))
            .addField("Ghost-hand", toggleStr(cfg.ghostInteract) + " (" + (int) cfg.ghostReach + "b)")
            .addField("Self-kill relocate", toggleStr(cfg.selfKillRelocate)
                + " (sky " + cfg.relocateMinSkyClearance + ", max " + cfg.relocateMaxAttempts + ")");
    }
}
