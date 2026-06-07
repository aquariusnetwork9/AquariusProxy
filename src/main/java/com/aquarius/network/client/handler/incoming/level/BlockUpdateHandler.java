package com.aquarius.network.client.handler.incoming.level;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.jspecify.annotations.NonNull;

import static com.aquarius.Globals.CACHE;

public class BlockUpdateHandler implements ClientEventLoopPacketHandler<ClientboundBlockUpdatePacket, ClientSession> {

    @Override
    public boolean applyAsync(@NonNull ClientboundBlockUpdatePacket packet, @NonNull ClientSession session) {
        return CACHE.getChunkCache().updateBlock(packet);
    }
}
