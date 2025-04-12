package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.cache.data.chunk.Chunk;
import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;

import static com.zenith.Globals.*;

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
