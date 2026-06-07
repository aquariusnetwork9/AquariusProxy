package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.cache.data.chunk.Chunk;
import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;

import static com.aquarius.Globals.*;

public class COLevelChunkWithLightHandler implements PacketHandler<ClientboundLevelChunkWithLightPacket, ServerSession> {
    @Override
    public ClientboundLevelChunkWithLightPacket apply(final ClientboundLevelChunkWithLightPacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        var coordOffset = coordObf.getCoordOffset(session);
        return new ClientboundLevelChunkWithLightPacket(
            coordOffset.offsetChunkX(packet.getX()),
            coordOffset.offsetChunkZ(packet.getZ()),
            coordOffset.obfuscateChunkSections(packet.getSections()),
            CONFIG.client.extra.coordObfuscation.obfuscateChunkHeightmap
                ? Chunk.EMPTY_HEIGHT_MAP
                : packet.getHeightMaps(),
            coordOffset.offsetBlockEntityInfos(packet.getBlockEntities()),
            CONFIG.client.extra.coordObfuscation.obfuscateChunkLighting
                ? CACHE.getChunkCache().createFullBrightLightData(packet.getLightData(), packet.getSections().length)
                : packet.getLightData()
        );
    }
}
