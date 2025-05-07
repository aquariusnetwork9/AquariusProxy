package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.cache.data.entity.EntityStandard;
import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.MODULE;

public class COEntityPositionSyncHandler implements PacketHandler<ClientboundEntityPositionSyncPacket, ServerSession> {
    @Override
    public ClientboundEntityPositionSyncPacket apply(final ClientboundEntityPositionSyncPacket packet, final ServerSession session) {
        var coordObf = MODULE.get(CoordObfuscation.class);
        var entity = CACHE.getEntityCache().get(packet.getId());
        if (entity == null && !coordObf.getSpectatorEntityIds().contains(packet.getId())) {
            return null;
        }
        if (entity instanceof EntityStandard e) {
            if (e.getEntityType() == EntityType.EYE_OF_ENDER) {
                return null;
            }
        }

        if (entity != null && !entity.getPassengerIds().isEmpty()
            && entity.getPassengerIds().contains(CACHE.getPlayerCache().getEntityId())) {
            coordObf.playerMovePos(session, packet.getX(), packet.getZ());
        }
        return new ClientboundEntityPositionSyncPacket(
            packet.getId(),
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getDeltaX(),
            packet.getDeltaY(),
            packet.getDeltaZ(),
            packet.getYaw(),
            packet.getPitch(),
            packet.isOnGround()
        );
    }
}
