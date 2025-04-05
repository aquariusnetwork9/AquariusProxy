package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;

import static com.zenith.Shared.MODULE;

public class COForgetLevelChunkHandler implements PacketHandler<ClientboundForgetLevelChunkPacket, ServerSession> {
    @Override
    public ClientboundForgetLevelChunkPacket apply(final ClientboundForgetLevelChunkPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundForgetLevelChunkPacket(
            coordObf.getCoordOffset(session).offsetChunkX(packet.getX()),
            coordObf.getCoordOffset(session).offsetChunkZ(packet.getZ())
        );
    }
}
