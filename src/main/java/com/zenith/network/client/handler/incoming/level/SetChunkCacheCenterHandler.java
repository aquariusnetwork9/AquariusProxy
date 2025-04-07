package com.zenith.network.client.handler.incoming.level;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
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
