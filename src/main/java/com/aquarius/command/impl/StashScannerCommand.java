package com.aquarius.command.impl;

import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.database.StashDatabase;
import com.aquarius.discord.Embed;
import com.aquarius.module.impl.StashScanner;
import com.aquarius.util.config.Config.Client.Extra.StashScanner.ScanMode;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.DATABASE;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.command.brigadier.ToggleArgumentType.getToggle;
import static com.aquarius.command.brigadier.ToggleArgumentType.toggle;

public class StashScannerCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("stashscanner")
            .category(CommandCategory.MODULE)
            .aliases("ss")
            .description("""
                Read-only stash census: walk storage containers, read each shulker's contents, classify it as
                a kit and snapshot it to Postgres. Never breaks/places. Requires the stash database. Alias: .ss
                """)
            .usageLines(
                "on/off | scan  (start a scan)",
                "mode region/list",
                "region <x> <y> <z> <radius>  (0 0 0 = use the bot's feet)",
                "band <down> <up>  (Y band around the origin)",
                "dryrun on/off",
                "seed [path]  (load kits.json into the catalog; default the bundled example)",
                "dump  (print unknown signatures to label)",
                "status"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("stashscanner")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.stashScanner.enabled = getToggle(c, "toggle");
                MODULE.get(StashScanner.class).syncEnabledFromConfig();
                c.getSource().getEmbed().title("Stash Scanner " + toggleStrCaps(CONFIG.client.extra.stashScanner.enabled));
            }))
            .then(literal("scan").executes(c -> {
                CONFIG.client.extra.stashScanner.enabled = true;
                MODULE.get(StashScanner.class).syncEnabledFromConfig();
                c.getSource().getEmbed().title("Stash Scanner: scanning")
                    .description(CONFIG.client.extra.stashScanner.dryRun ? "dry run (catalog seeding)" : "persisting snapshot");
            }))
            .then(literal("mode")
                .then(literal("region").executes(c -> { CONFIG.client.extra.stashScanner.mode = ScanMode.Region;
                    c.getSource().getEmbed().title("Scan mode: Region"); }))
                .then(literal("list").executes(c -> { CONFIG.client.extra.stashScanner.mode = ScanMode.List;
                    c.getSource().getEmbed().title("Scan mode: List (storage chests from the DB)"); })))
            .then(literal("region")
                .then(argument("x", integer()).then(argument("y", integer()).then(argument("z", integer())
                    .then(argument("radius", integer(1)).executes(c -> {
                        var cfg = CONFIG.client.extra.stashScanner;
                        cfg.originX = getInteger(c, "x"); cfg.originY = getInteger(c, "y"); cfg.originZ = getInteger(c, "z");
                        cfg.radius = getInteger(c, "radius"); cfg.mode = ScanMode.Region;
                        c.getSource().getEmbed().title("Scan region set")
                            .description("origin (" + cfg.originX + ", " + cfg.originY + ", " + cfg.originZ + "), radius " + cfg.radius);
                    }))))))
            .then(literal("band")
                .then(argument("down", integer(0)).then(argument("up", integer(0)).executes(c -> {
                    CONFIG.client.extra.stashScanner.bandDown = getInteger(c, "down");
                    CONFIG.client.extra.stashScanner.bandUp = getInteger(c, "up");
                    c.getSource().getEmbed().title("Y band: -" + getInteger(c, "down") + " .. +" + getInteger(c, "up"));
                }))))
            .then(literal("dryrun").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.stashScanner.dryRun = getToggle(c, "toggle");
                c.getSource().getEmbed().title("Dry run " + toggleStrCaps(CONFIG.client.extra.stashScanner.dryRun));
            })))
            .then(literal("seed").executes(c -> { seed(c, "kits.json"); })
                .then(argument("path", greedyString()).executes(c -> { seed(c, getString(c, "path")); })))
            .then(literal("dump").executes(c -> {
                StashDatabase db = DATABASE.getStashDatabase();
                if (db == null) { c.getSource().getEmbed().errorColor().title("Stash DB not enabled"); return; }
                List<String> sigs = db.unknownSignatures();
                c.getSource().getEmbed().title("Unknown signatures (" + sigs.size() + ")")
                    .description(sigs.isEmpty() ? "none — everything classified" : trunc(String.join("\n", sigs)));
            }))
            .then(literal("status").executes(c -> {
                var cfg = CONFIG.client.extra.stashScanner;
                c.getSource().getEmbed().title("Stash Scanner status")
                    .description(MODULE.get(StashScanner.class).statusLine()
                        + "\nmode " + cfg.mode + ", radius " + cfg.radius + ", dryRun " + cfg.dryRun
                        + (DATABASE.getStashDatabase() == null ? "\n⚠ stash database is OFF" : ""));
            }));
    }

    private int seed(com.mojang.brigadier.context.CommandContext<CommandContext> c, String path) {
        StashDatabase db = DATABASE.getStashDatabase();
        if (db == null) { c.getSource().getEmbed().errorColor().title("Stash DB not enabled"); return 1; }
        String json;
        try {
            Path p = Path.of(path);
            if (Files.exists(p)) {
                json = Files.readString(p);
            } else {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("stash/kits.example.json")) {
                    if (in == null) { c.getSource().getEmbed().errorColor().title("No kits.json and no bundled example"); return 1; }
                    json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    path = "(bundled example)";
                }
            }
        } catch (final Exception e) {
            c.getSource().getEmbed().errorColor().title("Failed reading " + path).description(String.valueOf(e.getMessage()));
            return 1;
        }
        int n = db.seedFromJson(json);
        c.getSource().getEmbed().title("Seeded " + n + " kit types").description("from " + path);
        return 1;
    }

    private static String trunc(String s) { return s.length() > 4000 ? s.substring(0, 4000) + "…" : s; }

    @Override
    public void defaultEmbed(Embed embed) {
        var cfg = CONFIG.client.extra.stashScanner;
        embed.primaryColor()
            .addField("Enabled", toggleStr(cfg.enabled))
            .addField("State", MODULE.get(StashScanner.class).statusLine())
            .addField("Mode", cfg.mode.toString())
            .addField("Region", "origin (" + cfg.originX + ", " + cfg.originY + ", " + cfg.originZ + "), radius " + cfg.radius)
            .addField("Dry run", toggleStr(cfg.dryRun))
            .addField("Database", DATABASE.getStashDatabase() == null ? "OFF" : "on");
    }
}
