package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLightUpdatePacket;

import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;

public class COLightUpdateHandler implements PacketHandler<ClientboundLightUpdatePacket, ServerSession> {
    @Override
    public ClientboundLightUpdatePacket apply(final ClientboundLightUpdatePacket packet, final ServerSession session) {
        if (CONFIG.client.extra.coordObfuscation.obfuscateChunkLighting) return null;
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundLightUpdatePacket(
            coordObf.getCoordOffset(session).offsetChunkX(packet.getX()),
            coordObf.getCoordOffset(session).offsetChunkZ(packet.getZ()),
            packet.getLightData()
        );
    }
}
