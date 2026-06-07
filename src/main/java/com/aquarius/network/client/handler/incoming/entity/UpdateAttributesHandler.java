package com.aquarius.network.client.handler.incoming.entity;

import com.aquarius.cache.data.entity.Entity;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateAttributesPacket;
import org.jspecify.annotations.NonNull;

import static com.aquarius.Globals.CACHE;

public class UpdateAttributesHandler implements ClientEventLoopPacketHandler<ClientboundUpdateAttributesPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundUpdateAttributesPacket packet, @NonNull ClientSession session) {
        Entity entity = CACHE.getEntityCache().get(packet.getEntityId());
        if (entity != null) {
            entity.updateAttributes(packet.getAttributes());
        }
        return true;
    }
}
