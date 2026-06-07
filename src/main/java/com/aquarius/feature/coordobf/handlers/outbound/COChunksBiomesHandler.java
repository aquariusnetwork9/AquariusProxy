package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkBiomeData;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunksBiomesPacket;

import java.util.stream.Collectors;

import static com.aquarius.Globals.MODULE;

public class COChunksBiomesHandler implements PacketHandler<ClientboundChunksBiomesPacket, ServerSession> {
    @Override
    public ClientboundChunksBiomesPacket apply(final ClientboundChunksBiomesPacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundChunksBiomesPacket(packet.getChunkBiomeData().stream()
            .map(biomeData -> new ChunkBiomeData(
                coordObf.getCoordOffset(session).offsetChunkX(biomeData.getX()),
                coordObf.getCoordOffset(session).offsetChunkZ(biomeData.getZ()),
                coordObf.getCoordOffset(session).obfuscateBiomePalettes(biomeData.getPalettes())
            ))
            .collect(Collectors.toList()));
    }
}
