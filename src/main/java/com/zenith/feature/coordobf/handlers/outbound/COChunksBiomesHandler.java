package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkBiomeData;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunksBiomesPacket;

import java.util.stream.Collectors;

public class COChunksBiomesHandler implements PacketHandler<ClientboundChunksBiomesPacket, ServerSession> {
    @Override
    public ClientboundChunksBiomesPacket apply(final ClientboundChunksBiomesPacket packet, final ServerSession session) {
        return new ClientboundChunksBiomesPacket(packet.getChunkBiomeData().stream()
            .map(biomeData -> new ChunkBiomeData(
                session.getCoordOffset().offsetChunkX(biomeData.getX()),
                session.getCoordOffset().offsetChunkZ(biomeData.getZ()),
                biomeData.getPalettes()
            ))
            .collect(Collectors.toList()));
    }
}
