package com.zenith.network.server.handler.spectator.incoming.movement;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.feature.spectator.SpectatorSync;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.jspecify.annotations.NonNull;

public class PlayerRotationSpectatorHandler implements PacketHandler<ServerboundMovePlayerRotPacket, ServerSession> {
    @Override
    public ServerboundMovePlayerRotPacket apply(@NonNull ServerboundMovePlayerRotPacket packet, @NonNull ServerSession session) {
        if (!session.isLoggedIn()) return null;
        session.getSpectatorPlayerCache()
                .setYaw(packet.getYaw())
                .setPitch(packet.getPitch());
        SpectatorSync.updateSpectatorPosition(session);
        return null;
    }
}
