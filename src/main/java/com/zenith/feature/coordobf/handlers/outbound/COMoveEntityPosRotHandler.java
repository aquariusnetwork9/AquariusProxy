package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.cache.data.entity.Entity;
import com.zenith.cache.data.entity.EntityStandard;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;

import static com.zenith.Shared.CACHE;

public class COMoveEntityPosRotHandler implements PacketHandler<ClientboundMoveEntityPosRotPacket, ServerSession> {
    @Override
    public ClientboundMoveEntityPosRotPacket apply(final ClientboundMoveEntityPosRotPacket packet, final ServerSession session) {
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
