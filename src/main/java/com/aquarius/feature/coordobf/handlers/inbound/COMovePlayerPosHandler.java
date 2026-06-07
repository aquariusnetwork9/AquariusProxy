package com.aquarius.feature.coordobf.handlers.inbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;

import static com.aquarius.Globals.MODULE;

public class COMovePlayerPosHandler implements PacketHandler<ServerboundMovePlayerPosPacket, ServerSession> {
    @Override
    public ServerboundMovePlayerPosPacket apply(final ServerboundMovePlayerPosPacket packet, final ServerSession session) {
        var coordObf = MODULE.get(CoordObfuscation.class);
        if (!coordObf.getPlayerState(session).isInGame()) {
            return null;
        }
        coordObf.playerMovePos(session, coordObf.getCoordOffset(session).reverseOffsetX(packet.getX()), coordObf.getCoordOffset(session).reverseOffsetZ(packet.getZ()));
        return new ServerboundMovePlayerPosPacket(
            packet.isOnGround(),
            packet.isHorizontalCollision(),
            coordObf.getCoordOffset(session).reverseOffsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).reverseOffsetZ(packet.getZ())
        );
    }
}
