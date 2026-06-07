package com.aquarius.network.client.handler.incoming.level;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunksBiomesPacket;

import static com.aquarius.Globals.CACHE;

public class ChunksBiomesHandler implements ClientEventLoopPacketHandler<ClientboundChunksBiomesPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundChunksBiomesPacket packet, final ClientSession session) {
        return CACHE.getChunkCache().handleChunkBiomes(packet);
    }
}
