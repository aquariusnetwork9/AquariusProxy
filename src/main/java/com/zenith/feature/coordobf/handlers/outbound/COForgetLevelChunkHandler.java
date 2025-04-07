package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;

import static com.zenith.Globals.MODULE;

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
