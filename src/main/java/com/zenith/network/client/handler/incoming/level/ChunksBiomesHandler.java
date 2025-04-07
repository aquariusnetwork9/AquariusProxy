package com.zenith.network.client.handler.incoming.level;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunksBiomesPacket;

import static com.zenith.Globals.CACHE;

public class ChunksBiomesHandler implements ClientEventLoopPacketHandler<ClientboundChunksBiomesPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundChunksBiomesPacket packet, final ClientSession session) {
        return CACHE.getChunkCache().handleChunkBiomes(packet);
    }
}
