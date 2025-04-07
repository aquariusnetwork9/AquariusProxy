package com.zenith.network.client.handler.incoming.level;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.jspecify.annotations.NonNull;

import static com.zenith.Globals.CACHE;

public class BlockUpdateHandler implements ClientEventLoopPacketHandler<ClientboundBlockUpdatePacket, ClientSession> {

    @Override
    public boolean applyAsync(@NonNull ClientboundBlockUpdatePacket packet, @NonNull ClientSession session) {
        return CACHE.getChunkCache().updateBlock(packet);
    }
}
