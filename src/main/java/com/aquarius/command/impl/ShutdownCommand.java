package com.aquarius.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.Proxy;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.discord.Embed;

public class ShutdownCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("shutdown")
            .category(CommandCategory.MANAGE)
            .description("""
                Shuts down AquariusProxy, without letting the launcher restart it.
                """)
            .aliases("exit")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("shutdown").requires(Command::validateAccountOwner).executes(c -> {
            c.getSource().getSource().logEmbed(c.getSource(), Embed.builder()
                .title("Shutting down...")
                .errorColor()
            );
            c.getSource().setNoOutput(true);
            Proxy.getInstance().stop(false);
        });
    }
}
