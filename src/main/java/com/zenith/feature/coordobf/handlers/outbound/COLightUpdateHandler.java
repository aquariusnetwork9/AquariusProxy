package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLightUpdatePacket;

import static com.zenith.Globals.MODULE;

public class COLightUpdateHandler implements PacketHandler<ClientboundLightUpdatePacket, ServerSession> {
    @Override
    public ClientboundLightUpdatePacket apply(final ClientboundLightUpdatePacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundLightUpdatePacket(
            coordObf.getCoordOffset(session).offsetChunkX(packet.getX()),
            coordObf.getCoordOffset(session).offsetChunkZ(packet.getZ()),
            packet.getLightData()
        );
    }
}
