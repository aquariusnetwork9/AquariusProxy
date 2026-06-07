package com.aquarius.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.aquarius.Proxy;
import com.aquarius.cache.data.entity.EntityPlayer;
import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;

import static com.aquarius.Globals.CACHE;
import static java.util.Objects.nonNull;

public class RespawnCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("respawn")
            .category(CommandCategory.MODULE)
            .description("Performs a respawn")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("respawn")
            .executes(c -> {
                final ClientSession client = Proxy.getInstance().getClient();
                if (nonNull(client)) {
                    client.sendAsync(new ServerboundClientCommandPacket(ClientCommand.RESPAWN));
                }
                final EntityPlayer player = CACHE.getPlayerCache().getThePlayer();
                if (nonNull(player)) {
                    player.setHealth(20.0f);
                }
                c.getSource().getEmbed()
                    .title("Respawn performed")
                    .primaryColor();
            });
    }
}
