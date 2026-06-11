package com.aquarius.command.impl;

import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.discord.Embed;
import com.aquarius.feature.enchant.EnchantCost;
import com.aquarius.feature.enchant.EnchantCost.EnchItem;
import com.aquarius.feature.enchant.EnchantOptimizer;
import com.aquarius.feature.enchant.EnchantTemplates;
import com.aquarius.module.impl.Enchanter;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;

public class EnchanterCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("enchanter")
            .category(CommandCategory.MODULE)
            .aliases("enc")
            .description("""
                Auto-build max-template gear in an anvil station. Auto-discovers the anvil pillar + chests
                (gear input, enchanted-book sources, finished output) within a radius, then applies a built-in
                max template to each item using the cheapest anvil combine order. Alias: .enc
                """)
            .usageLines(
                "on/off",
                "scan  (re-discover the station layout)",
                "radius <n>  (container/anvil discovery radius, max 32)",
                "band <down> <up>  (Y range +/- feet to scan, default 3/3)",
                "variant <group> <choice>  (e.g. sword_damage smite, tool_yield silk_touch)",
                "curse <family> <vanishing|binding> on/off  (per-family curse opt-in; never on by default)",
                "templates  (print the resolved max templates)",
                "max <n>  (stop after n items, 0 = until input empty)",
                "xp on/off  (throw XP bottles from the bottle chest to fund the anvil)",
                "xpbuffer <levels>  (top up to step-cost + this before each anvil step)",
                "xpreserve <n>  (bottles to keep carried)",
                "pace <action> <settle> <fill> <anvil>  (tick delays)",
                "pauseplayer on/off",
                "autodc on/off  (disconnect when done)",
                "status  (print the discovered layout + progress)"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("enchanter")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.enchanter.enabled = getToggle(c, "toggle");
                MODULE.get(Enchanter.class).syncEnabledFromConfig();
                c.getSource().getEmbed().title("Enchanter " + toggleStrCaps(CONFIG.client.extra.enchanter.enabled));
            }))
            .then(literal("scan").executes(c -> {
                MODULE.get(Enchanter.class).requestRescan();
                c.getSource().getEmbed().title("Enchanter: re-scanning the station")
                    .description(CONFIG.client.extra.enchanter.enabled ? "" : "(module is off — turn it on first)");
            }))
            .then(literal("radius").then(argument("n", integer(1, 32)).executes(c -> {
                CONFIG.client.extra.enchanter.scanRadius = getInteger(c, "n");
                c.getSource().getEmbed().title("Scan radius: " + getInteger(c, "n") + " blocks");
            })))
            .then(literal("band")
                .then(argument("down", integer(0)).then(argument("up", integer(0)).executes(c -> {
                    CONFIG.client.extra.enchanter.bandDown = getInteger(c, "down");
                    CONFIG.client.extra.enchanter.bandUp = getInteger(c, "up");
                    c.getSource().getEmbed().title("Scan band: feetY -" + getInteger(c, "down") + " .. +" + getInteger(c, "up"));
                }))))
            .then(literal("variant")
                .then(argument("group", word()).then(argument("choice", word()).executes(c -> {
                    String group = getString(c, "group");
                    String choice = getString(c, "choice");
                    if (!EnchantTemplates.isVariantGroup(group)) {
                        c.getSource().getEmbed().title("Unknown variant group: " + group)
                            .description("Run `.enc templates` to see the groups.").errorColor();
                        return;
                    }
                    var vg = EnchantTemplates.template(EnchantTemplates.groupFamily(group)).variants.stream()
                        .filter(v -> v.name.equals(group)).findFirst().orElse(null);
                    if (vg == null || !vg.options.containsKey(choice)) {
                        c.getSource().getEmbed().title("Invalid choice for " + group + ": " + choice)
                            .description("Options: " + (vg == null ? "?" : String.join(", ", vg.choices()))).errorColor();
                        return;
                    }
                    CONFIG.client.extra.enchanter.variantChoices.put(group, choice);
                    c.getSource().getEmbed().title("Variant " + group + " = " + choice);
                })))
            )
            .then(literal("curse")
                .then(argument("family", word()).then(argument("curse", word()).then(argument("toggle", toggle()).executes(c -> {
                    String family = getString(c, "family");
                    String curse = getString(c, "curse").toLowerCase();
                    boolean on = getToggle(c, "toggle");
                    if (EnchantTemplates.template(family) == null) {
                        c.getSource().getEmbed().title("Unknown family: " + family)
                            .description("Families: " + String.join(", ", EnchantTemplates.families())).errorColor();
                        return;
                    }
                    if (!curse.equals("vanishing") && !curse.equals("binding")) {
                        c.getSource().getEmbed().title("Unknown curse: " + curse)
                            .description("Use `vanishing` or `binding`.").errorColor();
                        return;
                    }
                    if (!EnchantTemplates.supportsCurse(family, curse)) {
                        c.getSource().getEmbed().title(family + " can't hold Curse of " + cap(curse))
                            .description("Curse of Binding only applies to worn gear (armor + elytra).").errorColor();
                        return;
                    }
                    CONFIG.client.extra.enchanter.curseChoices.put(EnchantTemplates.curseKey(family, curse), on);
                    c.getSource().getEmbed().title("Curse of " + cap(curse) + " on " + family + " " + toggleStrCaps(on))
                        .description("Stock a " + curse + "-curse book in the station for this to apply.");
                })))))
            .then(literal("templates").executes(c -> {
                StringBuilder sb = new StringBuilder();
                for (String fam : EnchantTemplates.families()) {
                    sb.append(EnchantTemplates.describe(fam, CONFIG.client.extra.enchanter.variantChoices,
                            CONFIG.client.extra.enchanter.curseChoices))
                        .append("  -> ").append(costEstimate(fam)).append('\n');
                }
                c.getSource().getEmbed().title("Enchanter templates").description(sb.toString());
            }))
            .then(literal("max").then(argument("n", integer(0)).executes(c -> {
                CONFIG.client.extra.enchanter.maxItems = getInteger(c, "n");
                c.getSource().getEmbed().title("Max items: " + (getInteger(c, "n") == 0 ? "unlimited" : getInteger(c, "n")));
            })))
            .then(literal("xp").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.enchanter.throwXp = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Throw XP bottles " + toggleStrCaps(CONFIG.client.extra.enchanter.throwXp))
                    .description(CONFIG.client.extra.enchanter.throwXp ? "Needs a chest of XP bottles in the station." : "Bot will pause if it runs out of levels mid-run.");
            })))
            .then(literal("xpbuffer").then(argument("levels", integer(0)).executes(c -> {
                CONFIG.client.extra.enchanter.xpBufferLevels = getInteger(c, "levels");
                c.getSource().getEmbed().title("XP buffer: step cost + " + getInteger(c, "levels") + " levels");
            })))
            .then(literal("xpreserve").then(argument("n", integer(0)).executes(c -> {
                CONFIG.client.extra.enchanter.xpBottleReserve = getInteger(c, "n");
                c.getSource().getEmbed().title("XP bottle reserve: " + getInteger(c, "n"));
            })))
            .then(literal("pace")
                .then(argument("action", integer(1)).then(argument("settle", integer(1))
                    .then(argument("fill", integer(1)).then(argument("anvil", integer(1)).executes(c -> {
                        var cfg = CONFIG.client.extra.enchanter;
                        cfg.actionDelayTicks = getInteger(c, "action");
                        cfg.settleTicks = getInteger(c, "settle");
                        cfg.fillDelayTicks = getInteger(c, "fill");
                        cfg.anvilSettleTicks = getInteger(c, "anvil");
                        c.getSource().getEmbed().title("Pace set")
                            .description("action " + cfg.actionDelayTicks + ", settle " + cfg.settleTicks
                                + ", fill " + cfg.fillDelayTicks + ", anvil " + cfg.anvilSettleTicks);
                    }))))))
            .then(literal("pauseplayer").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.enchanter.pauseOnPlayer = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Pause on player " + toggleStrCaps(CONFIG.client.extra.enchanter.pauseOnPlayer));
            })))
            .then(literal("autodc").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.enchanter.autoDisconnect = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Auto-disconnect when done " + toggleStrCaps(CONFIG.client.extra.enchanter.autoDisconnect));
            })))
            .then(literal("status").executes(c -> {
                var module = MODULE.get(Enchanter.class);
                c.getSource().getEmbed().title("Enchanter status")
                    .description(module.statusLine() + "\n" + module.setupLine());
            }));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        var cfg = CONFIG.client.extra.enchanter;
        var module = MODULE.get(Enchanter.class);
        StringBuilder variants = new StringBuilder();
        if (cfg.variantChoices.isEmpty()) variants.append("(defaults)");
        else cfg.variantChoices.forEach((g, ch) -> variants.append(g).append("=").append(ch).append(" "));
        embed
            .primaryColor()
            .addField("Enabled", toggleStr(cfg.enabled))
            .addField("State", module.statusLine())
            .addField("Layout", module.setupLine())
            .addField("Scan", "radius " + cfg.scanRadius + ", band -" + cfg.bandDown + "/+" + cfg.bandUp)
            .addField("Variants", variants.toString())
            .addField("Curses", cursesSummary(cfg.curseChoices))
            .addField("Max items", cfg.maxItems == 0 ? "unlimited" : String.valueOf(cfg.maxItems))
            .addField("XP", cfg.throwXp ? "bottles on (buffer +" + cfg.xpBufferLevels + ", reserve " + cfg.xpBottleReserve + ")" : "off")
            .addField("Pace", "action " + cfg.actionDelayTicks + ", settle " + cfg.settleTicks
                + ", fill " + cfg.fillDelayTicks + ", anvil " + cfg.anvilSettleTicks)
            .addField("Safety", "player-pause " + toggleStr(cfg.pauseOnPlayer) + " (" + (int) cfg.playerPauseRange + "b)")
            .addField("Auto-dc", toggleStr(cfg.autoDisconnect));
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** One-line summary of which per-family curses are toggled on (curses are never on by default). */
    private static String cursesSummary(Map<String, Boolean> curseChoices) {
        List<String> on = new ArrayList<>();
        curseChoices.forEach((key, val) -> {
            if (Boolean.TRUE.equals(val)) {
                String[] fc = key.split("\\|", 2);
                on.add(fc.length == 2 ? fc[0] + "=" + fc[1] : key);
            }
        });
        return on.isEmpty() ? "(none — off by default)" : String.join(" ", on);
    }

    /** Estimated total anvil levels + XP bottles to build a family's template (the "saved" per-template cost). */
    private String costEstimate(String family) {
        var cfg = CONFIG.client.extra.enchanter;
        Map<String, Integer> target = EnchantTemplates.resolveFamily(family, cfg.variantChoices, cfg.curseChoices);
        if (target == null) return "?";
        List<EnchItem> books = new ArrayList<>();
        for (var e : target.entrySet()) books.add(EnchItem.book(e.getKey(), e.getValue()));
        EnchantOptimizer.Plan plan = EnchantOptimizer.optimize(EnchItem.item(new LinkedHashMap<>(), 0), books);
        if (!plan.feasible) return "INFEASIBLE under the 40 cap";
        int points = 0;
        for (EnchantOptimizer.Step s : plan.steps) points += EnchantCost.xpForLevel(s.cost + cfg.xpBufferLevels);
        int bottles = (int) Math.ceil(points / 7.0);   // ~7 XP per bottle average
        return "~" + plan.totalCost + " lvls, ~" + bottles + " bottles";
    }
}
