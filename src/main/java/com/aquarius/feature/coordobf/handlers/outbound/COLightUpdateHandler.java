package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLightUpdatePacket;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

public class COLightUpdateHandler implements PacketHandler<ClientboundLightUpdatePacket, ServerSession> {
    @Override
    public ClientboundLightUpdatePacket apply(final ClientboundLightUpdatePacket packet, final ServerSession session) {
        if (CONFIG.client.extra.coordObfuscation.obfuscateChunkLighting) return null;
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundLightUpdatePacket(
            coordObf.getCoordOffset(session).offsetChunkX(packet.getX()),
            coordObf.getCoordOffset(session).offsetChunkZ(packet.getZ()),
            packet.getLightData() // not obfuscated because we cancelled the packet already
        );
    }
}
