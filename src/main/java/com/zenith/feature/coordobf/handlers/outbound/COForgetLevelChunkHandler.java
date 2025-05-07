package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;

import static com.zenith.Globals.MODULE;

public class COForgetLevelChunkHandler implements PacketHandler<ClientboundForgetLevelChunkPacket, ServerSession> {
    @Override
    public ClientboundForgetLevelChunkPacket apply(final ClientboundForgetLevelChunkPacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundForgetLevelChunkPacket(
            coordObf.getCoordOffset(session).offsetChunkX(packet.getX()),
            coordObf.getCoordOffset(session).offsetChunkZ(packet.getZ())
        );
    }
}
