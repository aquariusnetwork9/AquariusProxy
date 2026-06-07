package com.aquarius.network.client.handler.incoming.entity;

import com.aquarius.cache.data.entity.Entity;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import org.jspecify.annotations.NonNull;

import static com.aquarius.Globals.CACHE;

public class MoveEntityPosHandler implements ClientEventLoopPacketHandler<ClientboundMoveEntityPosPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundMoveEntityPosPacket packet, @NonNull ClientSession session) {
        Entity entity = CACHE.getEntityCache().get(packet.getEntityId());
        if (entity != null) {
            entity
                .setX(entity.getX() + packet.getMoveX())
                .setY(entity.getY() + packet.getMoveY())
                .setZ(entity.getZ() + packet.getMoveZ());
        }
        return true;
    }
}
