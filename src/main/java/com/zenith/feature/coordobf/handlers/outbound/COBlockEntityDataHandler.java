package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEntityDataPacket;

public class COBlockEntityDataHandler implements PacketHandler<ClientboundBlockEntityDataPacket, ServerSession> {
    @Override
    public ClientboundBlockEntityDataPacket apply(final ClientboundBlockEntityDataPacket packet, final ServerSession session) {
        return new ClientboundBlockEntityDataPacket(
            session.getCoordOffset().offsetX(packet.getX()),
            packet.getY(),
            session.getCoordOffset().offsetZ(packet.getZ()),
            packet.getType(),
            packet.getNbt() == null ? null : session.getCoordOffset().offsetNbt(packet.getNbt())
        );
    }
}
