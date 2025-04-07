package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.api.command.Command;
import com.zenith.api.command.CommandCategory;
import com.zenith.api.command.CommandContext;
import com.zenith.api.command.CommandUsage;
import com.zenith.api.network.client.ClientSession;
import com.zenith.cache.data.entity.EntityPlayer;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;

import static com.zenith.Globals.CACHE;
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
