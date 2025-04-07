package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.api.command.Command;
import com.zenith.api.command.CommandCategory;
import com.zenith.api.command.CommandContext;
import com.zenith.api.command.CommandUsage;
import com.zenith.api.plugin.PluginInfo;
import com.zenith.discord.DiscordBot;

import java.util.Comparator;
import java.util.stream.Collectors;

import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.PLUGIN_MANAGER;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class PluginsCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("plugins")
            .category(CommandCategory.MANAGE)
            .description("""
             [BETA]
             
             Configures the ZenithProxy plugin manager.
             
             Plugins are user-created add-ons that add modules and commands.
             
             Plugins are only supported on the `java` release channel.
             """)
            .usageLines(
                "on/off",
                "list"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("plugins").requires(Command::validateAccountOwner)
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.plugins.enabled = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Plugins " + toggleStrCaps(CONFIG.plugins.enabled))
                    .addField("Plugins", toggleStr(CONFIG.plugins.enabled), false)
                    .description("Restart ZenithProxy for changes to take effect: `restart`")
                    .primaryColor();
                return OK;
            }))
            .then(literal("list").executes(c -> {
                var plugins = PLUGIN_MANAGER.getPluginInfos();
                String info = plugins.stream()
                    .sorted(Comparator.comparing(PluginInfo::id))
                    .map(p -> """
                         **%s**
                         * Version: %s
                         * Description: %s
                         * URL: %s
                         * Author(s): %s
                         * MC: %s
                         """.formatted(
                             p.id(),
                             p.version(),
                             p.description(),
                             p.url(),
                             String.join(", ", p.authors()),
                             String.join(", ", p.mcVersions())
                    ))
                    .map(DiscordBot::escape)
                    .collect(Collectors.joining("\n"));
                c.getSource().getEmbed()
                    .title("Loaded Plugins (" + plugins.size() + ")")
                    .description(plugins.isEmpty() ? "None" : info)
                    .primaryColor();
            }));
    }
}
