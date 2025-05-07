package com.zenith.network.client.handler.incoming.entity;

import com.zenith.cache.data.entity.Entity;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import org.jspecify.annotations.NonNull;

import static com.zenith.Globals.CACHE;

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
