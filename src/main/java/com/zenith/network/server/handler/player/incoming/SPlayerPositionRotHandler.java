package com.zenith.network.server.handler.player.incoming;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;

import static com.zenith.Globals.SERVER_LOG;

public class SPlayerPositionRotHandler implements PacketHandler<ServerboundMovePlayerPosRotPacket, ServerSession> {
    @Override
    public ServerboundMovePlayerPosRotPacket apply(final ServerboundMovePlayerPosRotPacket packet, final ServerSession session) {
        if (session.isSpawned()) return packet;
        else {
            if (session.isSpawning()) {
                // todo: verify position matches spawn position
                SERVER_LOG.debug("[{}] Accepted spawn position", session.getName());
                session.setSpawned(true);
                session.setSpawning(false);
            } else {
                SERVER_LOG.debug("[{}] Cancelling pre-spawn position packet: {}", session.getName(), packet);
            }
            return null;
        }
    }
}
