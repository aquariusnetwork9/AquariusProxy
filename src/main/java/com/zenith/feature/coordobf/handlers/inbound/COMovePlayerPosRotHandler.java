package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;

import static com.zenith.Shared.CACHE;
import static com.zenith.Shared.MODULE;

public class COMovePlayerPosRotHandler implements PacketHandler<ServerboundMovePlayerPosRotPacket, ServerSession> {
    @Override
    public ServerboundMovePlayerPosRotPacket apply(final ServerboundMovePlayerPosRotPacket packet, final ServerSession session) {
        if (!session.isInGame()) {
            if (session.isSpectator()) {
                session.setInGame(true);
            } else {
                if (packet.getX() == session.getCoordOffset().offsetX(CACHE.getPlayerCache().getX())
                    && packet.getY() == CACHE.getPlayerCache().getY()
                    && packet.getZ() == session.getCoordOffset().offsetZ(CACHE.getPlayerCache().getZ())) {
                    session.setInGame(true);
                } else {
                    MODULE.get(CoordObfuscator.class).info("Received pos: {} {} {} but expected: {} {} {}",
                                    packet.getX(),
                                    packet.getY(),
                                    packet.getZ(),
                                    session.getCoordOffset().offsetX(CACHE.getPlayerCache().getX()),
                                    CACHE.getPlayerCache().getY(),
                                    session.getCoordOffset().offsetZ(CACHE.getPlayerCache().getZ()));
                    session.sendAsync(new ClientboundPlayerPositionPacket(
                        CACHE.getPlayerCache().getX(),
                        CACHE.getPlayerCache().getY(),
                        CACHE.getPlayerCache().getZ(),
                        CACHE.getPlayerCache().getYaw(),
                        CACHE.getPlayerCache().getPitch(),
                        0
                    ));
                    return null;
                }
            }
        }
        CoordObfuscator coordObfuscator = MODULE.get(CoordObfuscator.class);
        coordObfuscator.playerMovePos(session, session.getCoordOffset().reverseOffsetX(packet.getX()), session.getCoordOffset().reverseOffsetZ(packet.getZ()));
        if (coordObfuscator.isNextPlayerMovePacketIsTeleport()) {
            coordObfuscator.setNextPlayerMovePacketIsTeleport(false);
            return new ServerboundMovePlayerPosRotPacket(
                packet.isOnGround(),
                coordObfuscator.getServerTeleportPos().getX(),
                coordObfuscator.getServerTeleportPos().getY(),
                coordObfuscator.getServerTeleportPos().getZ(),
                packet.getYaw(),
                packet.getPitch()
            );
        }
        return new ServerboundMovePlayerPosRotPacket(
            packet.isOnGround(),
            session.getCoordOffset().reverseOffsetX(packet.getX()),
            packet.getY(),
            session.getCoordOffset().reverseOffsetZ(packet.getZ()),
            packet.getYaw(),
            packet.getPitch()
        );
    }
}
