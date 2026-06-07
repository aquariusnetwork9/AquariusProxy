package com.aquarius.network.client.handler.incoming;

import com.aquarius.cache.data.chunk.WorldBorderData;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundInitializeBorderPacket;

import static com.aquarius.Globals.CACHE;

public class WorldBorderInitializeHandler implements ClientEventLoopPacketHandler<ClientboundInitializeBorderPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundInitializeBorderPacket packet, final ClientSession session) {
        CACHE.getChunkCache().setWorldBorderData(new WorldBorderData(
                packet.getNewCenterX(),
                packet.getNewCenterZ(),
                packet.getNewSize(),
                packet.getNewAbsoluteMaxSize(),
                packet.getWarningBlocks(),
                packet.getWarningTime()
        ));
        return true;
    }
}
