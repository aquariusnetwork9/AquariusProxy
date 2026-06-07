package com.aquarius.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.Proxy;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.module.impl.AutoReconnect;

import static com.aquarius.Globals.DISCORD_LOG;
import static com.aquarius.Globals.MODULE;

public class DisconnectCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("disconnect")
            .category(CommandCategory.CORE)
            .description("Disconnects AquariusProxy from the destination MC server")
            .aliases("dc")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("disconnect").executes(c -> {
            if (!Proxy.getInstance().isConnected()) {
                boolean loginCancelled = Proxy.getInstance().cancelLogin();
                boolean autoReconnectCancelled = MODULE.get(AutoReconnect.class).cancelAutoReconnect();
                if (autoReconnectCancelled) {
                    c.getSource().getEmbed()
                        .title("AutoReconnect Cancelled");
                    return;
                }
                if (loginCancelled) {
                    c.getSource().getEmbed()
                            .title("Login Cancelled");
                    return;
                }
                c.getSource().getEmbed()
                        .title("Already Disconnected!");
            } else {
                try {
                    Proxy.getInstance().disconnect();
                    MODULE.get(AutoReconnect.class).cancelAutoReconnect();
                } catch (final Exception e) {
                    DISCORD_LOG.error("Failed to disconnect", e);
                    c.getSource().getEmbed()
                            .title("Proxy Failed to Disconnect");
                }
            }
        });
    }
}
