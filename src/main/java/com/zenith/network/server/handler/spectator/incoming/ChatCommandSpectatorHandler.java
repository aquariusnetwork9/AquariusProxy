package com.zenith.network.server.handler.spectator.incoming;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;

import static com.zenith.Globals.*;

public class ChatCommandSpectatorHandler implements PacketHandler<ServerboundChatCommandPacket, ServerSession> {
    @Override
    public ServerboundChatCommandPacket apply(final ServerboundChatCommandPacket packet, final ServerSession session) {
        final String command = packet.getCommand();
        if (command.isBlank()) return packet;
        if (CONFIG.inGameCommands.slashCommands
            && CONFIG.inGameCommands.enable
            && CONFIG.server.spectator.fullCommandsEnabled
            && CONFIG.server.spectator.fullCommandsAcceptSlashCommands
            && (CONFIG.server.spectator.fullCommandsRequireRegularWhitelist
                ? PLAYER_LISTS.getWhitelist().contains(session.getProfileCache().getProfile().getId())
                : true)) {
            EXECUTOR.execute(() -> IN_GAME_COMMAND.handleInGameCommandSpectator(
                command,
                session,
                CONFIG.inGameCommands.slashCommandsReplacesServerCommands));
        }
        return null;
    }
}
