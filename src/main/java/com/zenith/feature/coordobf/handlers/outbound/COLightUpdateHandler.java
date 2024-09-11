package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLightUpdatePacket;

public class COLightUpdateHandler implements PacketHandler<ClientboundLightUpdatePacket, ServerSession> {
    @Override
    public ClientboundLightUpdatePacket apply(final ClientboundLightUpdatePacket packet, final ServerSession session) {
        return new ClientboundLightUpdatePacket(
            session.getCoordOffset().offsetChunkX(packet.getX()),
            session.getCoordOffset().offsetChunkZ(packet.getZ()),
            packet.getLightData()
        );
    }
}
