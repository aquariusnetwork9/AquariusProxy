package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.cache.data.entity.EntityStandard;
import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.MODULE;

public class COTeleportEntityHandler implements PacketHandler<ClientboundTeleportEntityPacket, ServerSession> {
    @Override
    public ClientboundTeleportEntityPacket apply(final ClientboundTeleportEntityPacket packet, final ServerSession session) {
        var entity = CACHE.getEntityCache().get(packet.getEntityId());
        if (entity instanceof EntityStandard e) {
            if (e.getEntityType() == EntityType.EYE_OF_ENDER) {
                return null;
            }
        }
        var coordObf = MODULE.get(CoordObfuscation.class);
        if (!entity.getPassengerIds().isEmpty()
            && entity.getPassengerIds().contains(CACHE.getPlayerCache().getEntityId())) {
            coordObf.playerMovePos(session, packet.getX(), packet.getZ());
        }
        return new ClientboundTeleportEntityPacket(
            packet.getEntityId(),
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getYaw(),
            packet.getPitch(),
            packet.isOnGround()
        );
    }
}
