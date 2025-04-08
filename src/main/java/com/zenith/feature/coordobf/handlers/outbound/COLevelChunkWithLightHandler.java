package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.cache.data.chunk.Chunk;
import com.zenith.feature.coordobf.CoordOffset;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;

import static com.zenith.Globals.*;

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
            CONFIG.client.extra.coordObfuscation.obfuscateChunkHeightmap ? Chunk.EMPTY_HEIGHT_MAP : packet.getHeightMaps(),
            coordOffset.offsetBlockEntityInfos(packet.getBlockEntities()),
            CONFIG.client.extra.coordObfuscation.obfuscateChunkLighting ? CACHE.getChunkCache().createFullBrightLightData(packet.getLightData(), sections.length) : packet.getLightData()
        );
    }
}
