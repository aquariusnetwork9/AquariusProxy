package com.zenith.network.client.handler.incoming.level;

import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetChunkCacheCenterPacket;

import static com.zenith.Globals.CACHE;

public class SetChunkCacheCenterHandler implements ClientEventLoopPacketHandler<ClientboundSetChunkCacheCenterPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundSetChunkCacheCenterPacket packet, final ClientSession session) {
        CACHE.getChunkCache().setCenterX(packet.getChunkX());
        CACHE.getChunkCache().setCenterZ(packet.getChunkZ());
        return true;
    }
}
