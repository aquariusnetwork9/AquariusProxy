package com.aquarius.network.client.handler.incoming.entity;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTakeItemEntityPacket;
import org.jspecify.annotations.NonNull;

import static com.aquarius.Globals.CACHE;
import static java.util.Objects.nonNull;

public class TakeItemEntityHandler implements ClientEventLoopPacketHandler<ClientboundTakeItemEntityPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundTakeItemEntityPacket packet, @NonNull ClientSession session) {
        if (nonNull(CACHE.getEntityCache().get(packet.getCollectedEntityId()))) {
            CACHE.getEntityCache().remove(packet.getCollectedEntityId());
            return true;
        }
        return true;
    }
}
