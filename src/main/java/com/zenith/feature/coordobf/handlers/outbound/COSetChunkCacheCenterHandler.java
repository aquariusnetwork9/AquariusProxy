package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetChunkCacheCenterPacket;

import static com.zenith.Globals.MODULE;

public class COSetChunkCacheCenterHandler implements PacketHandler<ClientboundSetChunkCacheCenterPacket, ServerSession> {
    @Override
    public ClientboundSetChunkCacheCenterPacket apply(final ClientboundSetChunkCacheCenterPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundSetChunkCacheCenterPacket(
            coordObf.getCoordOffset(session).offsetChunkX(packet.getChunkX()),
            coordObf.getCoordOffset(session).offsetChunkZ(packet.getChunkZ())
        );
    }
}
