package com.zenith.network.server.handler.spectator.incoming.movement;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.feature.spectator.SpectatorSync;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.jspecify.annotations.NonNull;

public class PlayerPositionSpectatorHandler implements PacketHandler<ServerboundMovePlayerPosPacket, ServerSession> {
    @Override
    public ServerboundMovePlayerPosPacket apply(@NonNull ServerboundMovePlayerPosPacket packet, @NonNull ServerSession session) {
        if (!session.isLoggedIn()) return null;
        session.getSpectatorPlayerCache()
                .setX(packet.getX())
                .setY(packet.getY())
                .setZ(packet.getZ());
        SpectatorSync.updateSpectatorPosition(session);
        SpectatorSync.checkSpectatorPositionOutOfRender(session);
        return null;
    }
}
