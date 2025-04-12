package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetChunkCacheCenterPacket;

import static com.zenith.Globals.MODULE;

public class COSetChunkCacheCenterHandler implements PacketHandler<ClientboundSetChunkCacheCenterPacket, ServerSession> {
    @Override
    public ClientboundSetChunkCacheCenterPacket apply(final ClientboundSetChunkCacheCenterPacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundSetChunkCacheCenterPacket(
            coordObf.getCoordOffset(session).offsetChunkX(packet.getChunkX()),
            coordObf.getCoordOffset(session).offsetChunkZ(packet.getChunkZ())
        );
    }
}
