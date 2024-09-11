package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundOpenSignEditorPacket;

public class COOpenSignEditorHandler implements PacketHandler<ClientboundOpenSignEditorPacket, ServerSession> {
    @Override
    public ClientboundOpenSignEditorPacket apply(final ClientboundOpenSignEditorPacket packet, final ServerSession session) {
        return new ClientboundOpenSignEditorPacket(
            session.getCoordOffset().offsetX(packet.getX()),
            packet.getY(),
            session.getCoordOffset().offsetZ(packet.getZ()),
            packet.isFrontText()
        );
    }
}
