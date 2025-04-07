package com.zenith.network.client.handler.incoming.entity;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import com.zenith.cache.data.entity.Entity;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import org.jspecify.annotations.NonNull;

import static com.zenith.Globals.CACHE;
import static java.util.Objects.isNull;

public class MoveEntityPosHandler implements ClientEventLoopPacketHandler<ClientboundMoveEntityPosPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundMoveEntityPosPacket packet, @NonNull ClientSession session) {
        Entity entity = CACHE.getEntityCache().get(packet.getEntityId());
        if (isNull(entity)) return false;
        entity.setX(entity.getX() + packet.getMoveX())
                .setY(entity.getY() + packet.getMoveY())
                .setZ(entity.getZ() + packet.getMoveZ());
        return true;
    }
}
