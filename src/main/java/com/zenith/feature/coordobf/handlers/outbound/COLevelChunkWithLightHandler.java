package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;

public class COLevelChunkWithLightHandler implements PacketHandler<ClientboundLevelChunkWithLightPacket, ServerSession> {
    @Override
    public ClientboundLevelChunkWithLightPacket apply(final ClientboundLevelChunkWithLightPacket packet, final ServerSession session) {
        return session.getCoordOffset().shouldAddBedrockLayerToChunkData()
            ? offsetPacket(packet, session, session.getCoordOffset().addBedrockLayerToChunkData(packet))
            : offsetPacket(packet, session, packet.getSections());
    }

    private ClientboundLevelChunkWithLightPacket offsetPacket(final ClientboundLevelChunkWithLightPacket packet, final ServerSession session, final ChunkSection[] sections) {
        return new ClientboundLevelChunkWithLightPacket(
            session.getCoordOffset().offsetChunkX(packet.getX()),
            session.getCoordOffset().offsetChunkZ(packet.getZ()),
            sections,
            packet.getHeightMaps(),
            session.getCoordOffset().offsetBlockEntityInfos(packet.getBlockEntities()),
            packet.getLightData()
        );
    }
}
