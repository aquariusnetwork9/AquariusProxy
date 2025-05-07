package com.zenith.feature.actionlimiter.handlers.outbound;

import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import com.zenith.util.math.MathHelper;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveMinecartPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CONFIG;

public class ALMoveMinecartHandler implements PacketHandler<ClientboundMoveMinecartPacket, ServerSession> {
    @Override
    public ClientboundMoveMinecartPacket apply(final ClientboundMoveMinecartPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.actionLimiter.allowMovement) return packet;
        var entity = CACHE.getEntityCache().get(packet.getEntityId());
        if (entity == null) return packet;
        if (entity.getEntityType() != EntityType.MINECART) return packet;
        if (entity.getPassengerIds().isEmpty()) return packet;
        if (entity.getPassengerIds().contains(CACHE.getPlayerCache().getEntityId())) {
            var lastLerp = packet.getLerpSteps().getLast();
            // player is passenger and their pos will now match the vehicle pos
            if (MathHelper.distance2d(
                CONFIG.client.extra.actionLimiter.movementHomeX,
                CONFIG.client.extra.actionLimiter.movementHomeZ,
                lastLerp.x(),
                lastLerp.z()
            ) > CONFIG.client.extra.actionLimiter.movementDistance
            ) {
                session.disconnect("ActionLimiter: Movement not allowed");
                return null;
            }
        }
        return packet;
    }
}
