package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.cache.data.entity.Entity;
import com.zenith.cache.data.entity.EntityStandard;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;

import static com.zenith.Globals.CACHE;

public class COMoveEntityPosHandler implements PacketHandler<ClientboundMoveEntityPosPacket, ServerSession> {
    @Override
    public ClientboundMoveEntityPosPacket apply(final ClientboundMoveEntityPosPacket packet, final ServerSession session) {
        Entity entity = CACHE.getEntityCache().get(packet.getEntityId());
        if (entity == null) return null;
        if (entity instanceof EntityStandard e) {
            if (e.getEntityType() == EntityType.EYE_OF_ENDER) {
                return null;
            }
        }
        return packet;
    }
}
