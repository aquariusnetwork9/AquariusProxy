package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLightUpdatePacket;

import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;

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
