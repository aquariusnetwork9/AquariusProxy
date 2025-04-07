package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.feature.coordobf.CoordOffset;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;

import static com.zenith.Globals.MODULE;

public class COLevelChunkWithLightHandler implements PacketHandler<ClientboundLevelChunkWithLightPacket, ServerSession> {
    @Override
    public ClientboundLevelChunkWithLightPacket apply(final ClientboundLevelChunkWithLightPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return coordObf.getCoordOffset(session).shouldAddBedrockLayerToChunkData()
            ? offsetPacket(packet, coordObf.getCoordOffset(session), coordObf.getCoordOffset(session).addBedrockLayerToChunkData(packet))
            : offsetPacket(packet, coordObf.getCoordOffset(session), packet.getSections());
    }

    private ClientboundLevelChunkWithLightPacket offsetPacket(
        final ClientboundLevelChunkWithLightPacket packet,
        final CoordOffset coordOffset,
        final ChunkSection[] sections
    ) {
        return new ClientboundLevelChunkWithLightPacket(
            coordOffset.offsetChunkX(packet.getX()),
            coordOffset.offsetChunkZ(packet.getZ()),
            sections,
            packet.getHeightMaps(),
            coordOffset.offsetBlockEntityInfos(packet.getBlockEntities()),
            packet.getLightData()
        );
    }
}
