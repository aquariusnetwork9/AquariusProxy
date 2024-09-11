package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetChunkCacheCenterPacket;

public class COSetChunkCacheCenterHandler implements PacketHandler<ClientboundSetChunkCacheCenterPacket, ServerSession> {
    @Override
    public ClientboundSetChunkCacheCenterPacket apply(final ClientboundSetChunkCacheCenterPacket packet, final ServerSession session) {
        return new ClientboundSetChunkCacheCenterPacket(
                session.getCoordOffset().offsetChunkX(packet.getChunkX()),
                session.getCoordOffset().offsetChunkZ(packet.getChunkZ())
        );
    }
}
