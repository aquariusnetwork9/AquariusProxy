package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEventPacket;

public class COBlockEventHandler implements PacketHandler<ClientboundBlockEventPacket, ServerSession> {
    @Override
    public ClientboundBlockEventPacket apply(final ClientboundBlockEventPacket packet, final ServerSession session) {
        return new ClientboundBlockEventPacket(
            session.getCoordOffset().offsetX(packet.getX()),
            packet.getY(),
            session.getCoordOffset().offsetZ(packet.getZ()),
            packet.getType(),
            packet.getValue(),
            packet.getBlockId()
        );
    }
}
