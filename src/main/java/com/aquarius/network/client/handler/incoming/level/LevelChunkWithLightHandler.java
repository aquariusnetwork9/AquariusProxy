package com.aquarius.network.client.handler.incoming.level;

import com.aquarius.event.client.ChunkDataEvent;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.jspecify.annotations.NonNull;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.EVENT_BUS;

public class LevelChunkWithLightHandler implements ClientEventLoopPacketHandler<ClientboundLevelChunkWithLightPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundLevelChunkWithLightPacket packet, @NonNull ClientSession session) {
        CACHE.getChunkCache().add(packet);
        EVENT_BUS.postAsync(new ChunkDataEvent(packet.getX(), packet.getZ()));
        return true;
    }
}
