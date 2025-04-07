package com.zenith.network.server.handler.player.incoming;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundCommandSuggestionPacket;

import static com.zenith.Globals.CONFIG;

public class CommandSuggestionHandler implements PacketHandler<ServerboundCommandSuggestionPacket, ServerSession> {
    @Override
    public ServerboundCommandSuggestionPacket apply(final ServerboundCommandSuggestionPacket packet, final ServerSession session) {
        if (CONFIG.inGameCommands.enable
            && CONFIG.inGameCommands.slashCommands
            && CONFIG.inGameCommands.slashCommandsReplacesServerCommands) return null;
        return packet;
    }
}
