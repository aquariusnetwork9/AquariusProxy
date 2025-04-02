package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkBiomeData;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunksBiomesPacket;

import java.util.stream.Collectors;

import static com.zenith.Shared.MODULE;

public class COChunksBiomesHandler implements PacketHandler<ClientboundChunksBiomesPacket, ServerSession> {
    @Override
    public ClientboundChunksBiomesPacket apply(final ClientboundChunksBiomesPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundChunksBiomesPacket(packet.getChunkBiomeData().stream()
            .map(biomeData -> new ChunkBiomeData(
                coordObf.getCoordOffset(session).offsetChunkX(biomeData.getX()),
                coordObf.getCoordOffset(session).offsetChunkZ(biomeData.getZ()),
                biomeData.getPalettes()
            ))
            .collect(Collectors.toList()));
    }
}
