package com.aquarius.command.impl;

import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.util.config.Config.Client.Extra.KitProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;

/**
 * Manage the named kit profiles ({@code CONFIG.client.extra.kits}) — the one place every gear flow's "kit" is
 * defined: which shulker to pull, what to equip, the pull mode, and the flight-checklist contents. Flows point at a
 * profile by name via their own commands ({@code regear profile}, {@code fly flightkit}, {@code fly ebouncekit}).
 * The full field set is also directly editable in config.json.
 */
public class KitCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("kit")
            .category(CommandCategory.MODULE)
            .description("Manage named kit profiles shared by spawn-regear / nether-flight gear-up / e-bounce resupply.")
            .usageLines(
                "list",
                "add <name>     del <name>     <name>  (show)",
                "<name> shulker <text>   <name> color <text>   <name> match <name|color|contents|elytracount>",
                "<name> elytracount <n>   <name> elytraonly on/off   <name> elytratarget <n>",
                "<name> equiparmor on/off   <name> equipelytra on/off   <name> totem on/off   <name> return on/off",
                "<name> elytras <n>   fireworks <n>   totems <n>   egaps <n>   echests <n>"
            )
            .build();
    }

    private static KitProfile find(com.mojang.brigadier.context.CommandContext<CommandContext> c) {
        return CONFIG.client.extra.kitProfile(getString(c, "name"));
    }

    private static void show(com.mojang.brigadier.context.CommandContext<CommandContext> c) {
        KitProfile p = find(c);
        if (p == null) { c.getSource().getEmbed().title("No kit profile '" + getString(c, "name") + "'"); return; }
        c.getSource().getEmbed().title("Kit profile: " + p.name).description(
            "shulker=" + p.shulkerName + " match=" + (p.matchByElytraCount ? "elytracount(" + p.elytraCount + ")"
                : p.matchByContents ? "contents" : p.matchByColor ? "color(" + p.shulkerColor + ")" : "name")
            + "\nequip: armor=" + p.equipArmor + " elytra=" + p.equipElytra + " offhandTotem=" + p.offhandTotem + " return=" + p.returnShulker
            + "\npull: elytraOnly=" + p.elytraOnly + " elytraTarget=" + p.elytraTarget
            + "\nchecklist: elytras=" + p.minElytras + " fireworks=" + p.minFireworks + " totems=" + p.minTotems
            + " egaps=" + p.minEgaps + " armor=" + p.minArmor + " echests=" + p.minEchests
            + " pickaxe=" + p.requirePickaxe + " weapon=" + p.wantWeapon);
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("kit")
            .then(literal("list").executes(c -> {
                StringBuilder b = new StringBuilder();
                var e = CONFIG.client.extra;
                for (KitProfile p : e.kits) {
                    String used = (p.name.equalsIgnoreCase(e.regear.profile) ? " [spawn]" : "")
                        + (p.name.equalsIgnoreCase(e.elytraPilot.flightKitProfile) ? " [flight]" : "")
                        + (p.name.equalsIgnoreCase(e.elytraPilot.ebounceKitProfile) ? " [ebounce]" : "");
                    b.append("• ").append(p.name).append("  shulker=").append(p.shulkerName).append(used).append('\n');
                }
                if (e.kits.isEmpty()) b.append("(none)");
                c.getSource().getEmbed().title("Kit profiles").description(b.toString().stripTrailing());
            }))
            .then(literal("add").then(argument("name", word()).executes(c -> {
                String name = getString(c, "name");
                if (CONFIG.client.extra.kitProfile(name) != null) {
                    c.getSource().getEmbed().title("Kit profile '" + name + "' already exists");
                } else {
                    CONFIG.client.extra.kits.add(new KitProfile(name));
                    c.getSource().getEmbed().title("Added kit profile '" + name + "'")
                        .description("Edit it with `kit " + name + " …` or in config.json, then assign it to a flow.");
                }
            })))
            .then(literal("del").then(argument("name", word()).executes(c -> {
                String name = getString(c, "name");
                boolean removed = CONFIG.client.extra.kits.removeIf(k -> k.name.equalsIgnoreCase(name));
                c.getSource().getEmbed().title(removed ? "Removed kit profile '" + name + "'" : "No kit profile '" + name + "'");
            })))
            .then(argument("name", word()).executes(KitCommand::show)
                .then(literal("shulker").then(argument("text", word()).executes(c -> {
                    KitProfile p = find(c);
                    if (p != null) { p.shulkerName = getString(c, "text"); p.matchByColor = false; p.matchByContents = false; p.matchByElytraCount = false; }
                    show(c);
                })))
                .then(literal("color").then(argument("text", word()).executes(c -> {
                    KitProfile p = find(c);
                    if (p != null) p.shulkerColor = getString(c, "text").toLowerCase();
                    show(c);
                })))
                .then(literal("match").then(argument("mode", word()).executes(c -> {
                    KitProfile p = find(c);
                    if (p != null) {
                        String m = getString(c, "mode").toLowerCase();
                        p.matchByColor = m.equals("color"); p.matchByContents = m.equals("contents"); p.matchByElytraCount = m.equals("elytracount");
                    }
                    show(c);
                })))
                .then(literal("elytracount").then(argument("n", integer(1)).executes(c -> { KitProfile p = find(c); if (p != null) p.elytraCount = getInteger(c, "n"); show(c); })))
                .then(literal("elytraonly").then(argument("toggle", toggle()).executes(c -> { KitProfile p = find(c); if (p != null) p.elytraOnly = getToggle(c, "toggle"); show(c); })))
                .then(literal("elytratarget").then(argument("n", integer(1)).executes(c -> { KitProfile p = find(c); if (p != null) p.elytraTarget = getInteger(c, "n"); show(c); })))
                .then(literal("equiparmor").then(argument("toggle", toggle()).executes(c -> { KitProfile p = find(c); if (p != null) p.equipArmor = getToggle(c, "toggle"); show(c); })))
                .then(literal("equipelytra").then(argument("toggle", toggle()).executes(c -> { KitProfile p = find(c); if (p != null) p.equipElytra = getToggle(c, "toggle"); show(c); })))
                .then(literal("totem").then(argument("toggle", toggle()).executes(c -> { KitProfile p = find(c); if (p != null) p.offhandTotem = getToggle(c, "toggle"); show(c); })))
                .then(literal("return").then(argument("toggle", toggle()).executes(c -> { KitProfile p = find(c); if (p != null) p.returnShulker = getToggle(c, "toggle"); show(c); })))
                .then(literal("elytras").then(argument("n", integer(0)).executes(c -> { KitProfile p = find(c); if (p != null) p.minElytras = getInteger(c, "n"); show(c); })))
                .then(literal("fireworks").then(argument("n", integer(0)).executes(c -> { KitProfile p = find(c); if (p != null) p.minFireworks = getInteger(c, "n"); show(c); })))
                .then(literal("totems").then(argument("n", integer(0)).executes(c -> { KitProfile p = find(c); if (p != null) p.minTotems = getInteger(c, "n"); show(c); })))
                .then(literal("egaps").then(argument("n", integer(0)).executes(c -> { KitProfile p = find(c); if (p != null) p.minEgaps = getInteger(c, "n"); show(c); })))
                .then(literal("echests").then(argument("n", integer(0)).executes(c -> { KitProfile p = find(c); if (p != null) p.minEchests = getInteger(c, "n"); show(c); }))));
    }
}
