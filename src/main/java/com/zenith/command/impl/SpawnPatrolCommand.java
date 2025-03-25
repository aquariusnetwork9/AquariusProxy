package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.Command;
import com.zenith.command.CommandUsage;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;
import com.zenith.discord.Embed;
import com.zenith.module.impl.SpawnPatrol;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Shared.*;
import static com.zenith.command.brigadier.CustomStringArgumentType.getString;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static com.zenith.command.util.CommandOutputHelper.playerListToString;

public class SpawnPatrolCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("spawnPatrol")
            .category(CommandCategory.MODULE)
            .description("""
            Patrols spawn and paths to any player it finds, killing them if you have kill aura enabled.
            """)
            .usageLines(
                "on/off",
                "random on/off",
                "goal <x> <z>",
                "goal <x> <y> <z>",
                "spook on/off",
                "spook onlyNakeds on/off",
                "spook stickyTarget on/off",
                "spook attackers on/off",
                "nether on/off",
                "kill on/off",
                "kill seconds <seconds>",
                "kill minDist <blocks>",
                "ignore add/del <player>",
                "ignore list"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("spawnPatrol")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.spawnPatrol.enabled = getToggle(c, "toggle");
                MODULE.get(SpawnPatrol.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("SpawnPatrol " + toggleStrCaps(CONFIG.client.extra.spawnPatrol.enabled));
                return OK;
            }))
            .then(literal("random").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.spawnPatrol.random = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Random " + toggleStrCaps(CONFIG.client.extra.spawnPatrol.random));
                return OK;
            })))
            .then(literal("goal")
                      .then(argument("x", integer()).then(argument("z", integer()).executes(c -> {
                          CONFIG.client.extra.spawnPatrol.goalX = getInteger(c, "x");
                          CONFIG.client.extra.spawnPatrol.goalZ = getInteger(c, "z");
                          CONFIG.client.extra.spawnPatrol.goalXZ = true;
                          c.getSource().getEmbed()
                              .title("Goal Set");
                          return OK;
                      })))
                      .then(argument("x", integer()).then(argument("y", integer(-64, 320)).then(argument("z", integer()).executes(c -> {
                          CONFIG.client.extra.spawnPatrol.goalX = getInteger(c, "x");
                          CONFIG.client.extra.spawnPatrol.goalY = getInteger(c, "y");
                          CONFIG.client.extra.spawnPatrol.goalZ = getInteger(c, "z");
                          CONFIG.client.extra.spawnPatrol.goalXZ = false;
                          c.getSource().getEmbed()
                              .title("Goal Set");
                          return OK;
                      })))))
            .then(literal("spook")
                      .then(argument("toggle", toggle()).executes(c -> {
                          CONFIG.client.extra.spawnPatrol.spook = getToggle(c, "toggle");
                          c.getSource().getEmbed()
                              .title("Spook " + toggleStrCaps(CONFIG.client.extra.spawnPatrol.spook));
                          return OK;
                      }))
                      .then(literal("onlyNakeds").then(argument("toggle", toggle()).executes(c -> {
                          CONFIG.client.extra.spawnPatrol.spookOnlyNakeds = getToggle(c, "toggle");
                          c.getSource().getEmbed()
                              .title("Spook Only Nakeds " + toggleStrCaps(CONFIG.client.extra.spawnPatrol.spookOnlyNakeds));
                          return OK;
                      })))
                      .then(literal("stickyTarget").then(argument("toggle", toggle()).executes(c -> {
                          CONFIG.client.extra.spawnPatrol.spookStickyTarget = getToggle(c, "toggle");
                          c.getSource().getEmbed()
                              .title("Spook Sticky Target " + toggleStrCaps(CONFIG.client.extra.spawnPatrol.spookStickyTarget));
                          return OK;
                      })))
                      .then(literal("attackers").then(argument("toggle", toggle()).executes(c -> {
                          CONFIG.client.extra.spawnPatrol.spookAttackers = getToggle(c, "toggle");
                          c.getSource().getEmbed()
                              .title("Spook Attackers " + toggleStrCaps(CONFIG.client.extra.spawnPatrol.spookAttackers));
                          return OK;
                      }))))
            .then(literal("nether").then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.spawnPatrol.nether = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Nether " + toggleStrCaps(CONFIG.client.extra.spawnPatrol.nether));
                return OK;
            })))
            .then(literal("kill")
                      .then(argument("toggle", toggle()).executes(c -> {
                            CONFIG.client.extra.spawnPatrol.kill = getToggle(c, "toggle");
                            c.getSource().getEmbed()
                                .title("/kill " + toggleStrCaps(CONFIG.client.extra.spawnPatrol.kill));
                            return OK;
                      }))
                      .then(literal("seconds").then(argument("seconds", integer()).executes(c -> {
                            CONFIG.client.extra.spawnPatrol.killSeconds = getInteger(c, "seconds");
                            c.getSource().getEmbed()
                                .title("/kill Seconds Set");
                            return OK;
                      })))
                      .then(literal("minDist").then(argument("blocks", integer()).executes(c -> {
                          CONFIG.client.extra.spawnPatrol.killMinDist = getInteger(c, "blocks");
                          c.getSource().getEmbed()
                              .title("/kill MinDist Set");
                          return OK;
                      })))
                      .then(literal("antiStuck").then(argument("toggle", toggle()).executes(c -> {
                          CONFIG.client.extra.spawnPatrol.killAntiStuck = getToggle(c, "toggle");
                          c.getSource().getEmbed()
                              .title("/kill AntiStuck " + toggleStrCaps(CONFIG.client.extra.spawnPatrol.killAntiStuck));
                          return OK;
                      })))
            )
            .then(literal("ignore")
                      .then(literal("add").then(argument("player", wordWithChars()).executes(c -> {
                          String player = getString(c, "player");
                          PLAYER_LISTS.getSpawnPatrolIgnoreList().add(player).ifPresentOrElse(e -> {
                              c.getSource().getEmbed()
                                  .title("Added " + player + " to ignore list")
                                  .description(playerListToString(PLAYER_LISTS.getSpawnPatrolIgnoreList()));
                          }, () -> {
                              c.getSource().getEmbed()
                                  .title("Failed");
                          });
                          return OK;
                      })))
                      .then(literal("del").then(argument("player", wordWithChars()).executes(c -> {
                          String player = getString(c, "player");
                          PLAYER_LISTS.getSpawnPatrolIgnoreList().remove(player);
                          c.getSource().getEmbed()
                              .title("Removed " + player + " from ignore list")
                              .description(playerListToString(PLAYER_LISTS.getSpawnPatrolIgnoreList()));
                          return OK;
                      })))
                      .then(literal("list").executes(c -> {
                            c.getSource().getEmbed()
                                .title("Ignore List")
                                .description(playerListToString(PLAYER_LISTS.getSpawnPatrolIgnoreList()));
                            return OK;
                      })));
    }

    @Override
    public void postPopulate(Embed embed) {
        embed
            .addField("SpawnPatrol", toggleStr(CONFIG.client.extra.spawnPatrol.enabled), false)
            .addField("Random", toggleStr(CONFIG.client.extra.spawnPatrol.random), false)
            .addField("Goal", CONFIG.client.extra.spawnPatrol.goalX
                + ", "
                + (CONFIG.client.extra.spawnPatrol.goalXZ ? "" : CONFIG.client.extra.spawnPatrol.goalY + ", ")
                + CONFIG.client.extra.spawnPatrol.goalZ, false)
            .addField("Spook", toggleStr(CONFIG.client.extra.spawnPatrol.spook), false)
            .addField("Spook Only Nakeds", toggleStr(CONFIG.client.extra.spawnPatrol.spookOnlyNakeds), false)
            .addField("Spook Sticky Target", toggleStr(CONFIG.client.extra.spawnPatrol.spookStickyTarget), false)
            .addField("Spook Attackers", toggleStr(CONFIG.client.extra.spawnPatrol.spookAttackers), false)
            .addField("Nether", toggleStr(CONFIG.client.extra.spawnPatrol.nether), false)
            .addField("Kill", toggleStr(CONFIG.client.extra.spawnPatrol.kill), false)
            .addField("Kill Seconds", CONFIG.client.extra.spawnPatrol.killSeconds, false)
            .addField("Kill MinDist", CONFIG.client.extra.spawnPatrol.killMinDist, false)
            .addField("Kill AntiStuck", toggleStr(CONFIG.client.extra.spawnPatrol.killAntiStuck), false)
            .primaryColor();
    }
}
