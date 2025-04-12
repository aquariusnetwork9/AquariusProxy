package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;

import static com.zenith.Globals.MODULE;

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
            coordObf.getCoordOffset(session).reverseOffsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).reverseOffsetZ(packet.getZ())
        );
    }
}
