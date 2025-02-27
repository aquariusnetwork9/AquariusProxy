package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.Command;
import com.zenith.command.CommandUsage;
import com.zenith.command.brigadier.CommandCategory;
import com.zenith.command.brigadier.CommandContext;
import com.zenith.discord.Embed;

import static com.zenith.Shared.CONFIG;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class PluginsCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("plugins")
            .category(CommandCategory.MANAGE)
            .description("""
             Configures the ZenithProxy plugin manager.
             
             Plugins are user-created add-ons that add modules and commands.
             
             Plugins are only supported on the `java` release channel.
             """)
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("plugins").requires(Command::validateAccountOwner)
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.plugins.enabled = getToggle(c, "toggle");
                c.getSource().getEmbed()
                    .title("Plugins " + toggleStrCaps(CONFIG.plugins.enabled))
                    .description("Restart ZenithProxy for changes to take effect: `restart`");
                return OK;
            }));
    }

    @Override
    public void postPopulate(Embed embed) {
        embed
            .addField("Plugins", toggleStr(CONFIG.plugins.enabled), false)
            .primaryColor();
    }
}
