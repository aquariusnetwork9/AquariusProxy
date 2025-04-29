package com.zenith.feature.actionlimiter.handlers.outbound;

import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import com.zenith.util.math.MathHelper;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CONFIG;

public class ALTeleportEntityHandler implements PacketHandler<ClientboundTeleportEntityPacket, ServerSession> {
    @Override
    public ClientboundTeleportEntityPacket apply(final ClientboundTeleportEntityPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.actionLimiter.allowMovement) return packet;
        var entity = CACHE.getEntityCache().get(packet.getEntityId());
        if (entity == null) return packet;
        if (entity.getPassengerIds().isEmpty()) return packet;
        if (entity.getPassengerIds().contains(CACHE.getPlayerCache().getEntityId())) {
            // player is passenger and their pos will now match the vehicle pos
            if (MathHelper.distance2d(
                CONFIG.client.extra.actionLimiter.movementHomeX,
                CONFIG.client.extra.actionLimiter.movementHomeZ,
                packet.getX(),
                packet.getZ()
            ) > CONFIG.client.extra.actionLimiter.movementDistance
            ) {
                session.disconnect("ActionLimiter: Movement not allowed");
                return null;
            }
        }
        return packet;
    }
}
