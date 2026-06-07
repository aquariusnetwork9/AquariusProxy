package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.cache.data.entity.EntityStandard;
import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.MODULE;

public class COTeleportEntityHandler implements PacketHandler<ClientboundTeleportEntityPacket, ServerSession> {
    @Override
    public ClientboundTeleportEntityPacket apply(final ClientboundTeleportEntityPacket packet, final ServerSession session) {
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
        return new ClientboundTeleportEntityPacket(
            packet.getId(),
            packet.getRelatives().contains(PositionElement.X) ? packet.getX() : coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            packet.getRelatives().contains(PositionElement.Z) ? packet.getZ() : coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getDeltaX(),
            packet.getDeltaY(),
            packet.getDeltaZ(),
            packet.getYaw(),
            packet.getPitch(),
            packet.getRelatives(),
            packet.isOnGround()
        );
    }
}
