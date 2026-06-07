package com.aquarius.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.Proxy;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.module.impl.AutoReconnect;

import static com.aquarius.Globals.EXECUTOR;
import static com.aquarius.Globals.MODULE;
import static com.aquarius.util.DisconnectMessages.SYSTEM_DISCONNECT;

public class ReconnectCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("reconnect")
            .category(CommandCategory.MANAGE)
            .description("""
            Disconnect and reconnects from the destination MC server.
            
            Can be used to perform a reconnect "queue skip" on 2b2t
            """)
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("reconnect").executes(c -> {
            EXECUTOR.execute(() -> {
                Proxy.getInstance().disconnect(SYSTEM_DISCONNECT);
                MODULE.get(AutoReconnect.class).cancelAutoReconnect();
                MODULE.get(AutoReconnect.class).scheduleAutoReconnect(2);
            });
        });
    }
}
