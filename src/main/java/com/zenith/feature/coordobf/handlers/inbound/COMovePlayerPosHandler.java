package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;

import static com.zenith.Globals.MODULE;

public class COMovePlayerPosHandler implements PacketHandler<ServerboundMovePlayerPosPacket, ServerSession> {
    @Override
    public ServerboundMovePlayerPosPacket apply(final ServerboundMovePlayerPosPacket packet, final ServerSession session) {
        var coordObf = MODULE.get(CoordObfuscator.class);
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
