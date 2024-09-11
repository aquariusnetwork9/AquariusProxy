package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;

import static com.zenith.Shared.MODULE;

public class COMovePlayerPosHandler implements PacketHandler<ServerboundMovePlayerPosPacket, ServerSession> {
    @Override
    public ServerboundMovePlayerPosPacket apply(final ServerboundMovePlayerPosPacket packet, final ServerSession session) {
        if (!session.isInGame()) {
            return null;
        }

        CoordObfuscator coordObfuscator = MODULE.get(CoordObfuscator.class);
        coordObfuscator.playerMovePos(session, session.getCoordOffset().reverseOffsetX(packet.getX()), session.getCoordOffset().reverseOffsetZ(packet.getZ()));
        if (coordObfuscator.isNextPlayerMovePacketIsTeleport()) {
            coordObfuscator.setNextPlayerMovePacketIsTeleport(false);
            MODULE.get(CoordObfuscator.class).info("Sending corrected teleport packet {} {} {}", coordObfuscator.getServerTeleportPos().getX(), coordObfuscator.getServerTeleportPos().getY(), coordObfuscator.getServerTeleportPos().getZ());
            return new ServerboundMovePlayerPosPacket(
                packet.isOnGround(),
                coordObfuscator.getServerTeleportPos().getX(),
                coordObfuscator.getServerTeleportPos().getY(),
                coordObfuscator.getServerTeleportPos().getZ()
            );
        }
        return new ServerboundMovePlayerPosPacket(
            packet.isOnGround(),
            session.getCoordOffset().reverseOffsetX(packet.getX()),
            packet.getY(),
            session.getCoordOffset().reverseOffsetZ(packet.getZ())
        );
    }
}
