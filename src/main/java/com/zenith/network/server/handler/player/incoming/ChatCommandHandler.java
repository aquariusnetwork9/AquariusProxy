package com.zenith.network.server.handler.player.incoming;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;

import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.IN_GAME_COMMAND;

public class ChatCommandHandler implements PacketHandler<ServerboundChatCommandPacket, ServerSession> {
    @Override
    public ServerboundChatCommandPacket apply(final ServerboundChatCommandPacket packet, final ServerSession session) {
        final String command = packet.getCommand();
        if (command.isBlank()) return packet;
        if (CONFIG.inGameCommands.slashCommands
            && CONFIG.inGameCommands.enable
            && (IN_GAME_COMMAND.handleInGameCommand(
                command,
                session,
                CONFIG.inGameCommands.slashCommandsReplacesServerCommands)
            || CONFIG.inGameCommands.slashCommandsReplacesServerCommands))
                return null;
        return packet;
    }
}
