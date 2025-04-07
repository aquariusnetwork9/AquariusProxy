package com.zenith.network.client.handler.incoming.level;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import org.jspecify.annotations.NonNull;

import static com.zenith.Globals.CACHE;

public class SectionBlocksUpdateHandler implements ClientEventLoopPacketHandler<ClientboundSectionBlocksUpdatePacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundSectionBlocksUpdatePacket packet, @NonNull ClientSession session) {
        return CACHE.getChunkCache().multiBlockUpdate(packet);
    }
}
