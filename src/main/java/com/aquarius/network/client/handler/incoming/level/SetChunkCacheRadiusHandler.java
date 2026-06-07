package com.aquarius.network.client.handler.incoming.level;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetChunkCacheRadiusPacket;

import static com.aquarius.Globals.CACHE;

public class SetChunkCacheRadiusHandler implements ClientEventLoopPacketHandler<ClientboundSetChunkCacheRadiusPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundSetChunkCacheRadiusPacket packet, final ClientSession session) {
        CACHE.getChunkCache().setServerViewDistance(packet.getViewDistance());
        return true;
    }
}
