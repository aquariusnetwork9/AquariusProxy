package com.zenith.feature.actionlimiter.handlers.inbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.util.math.MathHelper;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;

import static com.zenith.Globals.CONFIG;

public class ALMovePlayerPosHandler implements PacketHandler<ServerboundMovePlayerPosPacket, ServerSession> {
    @Override
    public ServerboundMovePlayerPosPacket apply(final ServerboundMovePlayerPosPacket packet, final ServerSession session) {
        if (CONFIG.client.extra.actionLimiter.allowMovement  || !session.isSpawned())
            return packet;
        if (packet.getY() <= CONFIG.client.extra.actionLimiter.movementMinY) {
            session.disconnect("ActionLimiter: Movement not allowed");
            return null;
        }
        if (MathHelper.distance2d(CONFIG.client.extra.actionLimiter.movementHomeX,
                                  CONFIG.client.extra.actionLimiter.movementHomeZ,
                                  packet.getX(),
                                  packet.getZ()) > CONFIG.client.extra.actionLimiter.movementDistance) {
            session.disconnect("ActionLimiter: Movement not allowed");
            return null;
        }
        return packet;
    }
}
