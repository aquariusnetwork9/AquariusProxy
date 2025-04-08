package com.zenith.feature.actionlimiter.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.util.math.MathHelper;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CONFIG;

public class ALPlayerPositionHandler implements PacketHandler<ClientboundPlayerPositionPacket, ServerSession> {
    @Override
    public ClientboundPlayerPositionPacket apply(final ClientboundPlayerPositionPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.actionLimiter.allowMovement)
            return packet;
        if (packet.getY() <= CONFIG.client.extra.actionLimiter.movementMinY) {
            session.disconnect("ActionLimiter: Movement not allowed");
            return null;
        }
        if (MathHelper.distance2d(
            CONFIG.client.extra.actionLimiter.movementHomeX,
            CONFIG.client.extra.actionLimiter.movementHomeZ,
            packet.getRelative().contains(PositionElement.X)
                ? packet.getX() + CACHE.getPlayerCache().getX()
                : packet.getX(),
            packet.getRelative().contains(PositionElement.Z)
                ? packet.getZ() + CACHE.getPlayerCache().getZ()
                : packet.getZ()
            ) > CONFIG.client.extra.actionLimiter.movementDistance
        ) {
            session.disconnect("ActionLimiter: Movement not allowed");
            return null;
        }
        return packet;
    }
}
