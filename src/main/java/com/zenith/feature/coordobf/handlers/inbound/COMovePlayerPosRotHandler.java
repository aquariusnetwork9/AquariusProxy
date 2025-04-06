package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.feature.coordobf.CoordOffset;
import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import com.zenith.util.math.MathHelper;
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
                CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
                if (MathHelper.isInRange(packet.getX(), coordObf.getCoordOffset(session).offsetX(CACHE.getPlayerCache().getX()), CoordOffset.EPSILON * 2)
                    && packet.getY() == CACHE.getPlayerCache().getY()
                    && MathHelper.isInRange(packet.getZ(), coordObf.getCoordOffset(session).offsetZ(CACHE.getPlayerCache().getZ()), CoordOffset.EPSILON * 2)) {
                    session.setInGame(true);
                } else {
                    coordObf.info("Received {} pos: {} {} {} but expected: {} {} {}",
                                    session.getProfileCache().getProfile().getName(),
                                    packet.getX(),
                                    packet.getY(),
                                    packet.getZ(),
                                    coordObf.getCoordOffset(session).offsetX(CACHE.getPlayerCache().getX()),
                                    CACHE.getPlayerCache().getY(),
                                    coordObf.getCoordOffset(session).offsetZ(CACHE.getPlayerCache().getZ()));
                    session.sendAsync(new ClientboundPlayerPositionPacket(
                        CACHE.getPlayerCache().getX(),
                        CACHE.getPlayerCache().getY(),
                        CACHE.getPlayerCache().getZ(),
                        CACHE.getPlayerCache().getYaw(),
                        CACHE.getPlayerCache().getPitch(),
                        session.getSpawnTeleportId()
                    ));
                    return null;
                }
            }
        }
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        double reverseOffsetX = coordObf.getCoordOffset(session).reverseOffsetX(packet.getX());
        double reverseOffsetZ = coordObf.getCoordOffset(session).reverseOffsetZ(packet.getZ());
        coordObf.playerMovePos(session, reverseOffsetX, reverseOffsetZ);
        if (coordObf.isNextPlayerMovePacketIsTeleport()) {
            coordObf.setNextPlayerMovePacketIsTeleport(false);
            return new ServerboundMovePlayerPosRotPacket(
                packet.isOnGround(),
                coordObf.getServerTeleportPos().getX(),
                coordObf.getServerTeleportPos().getY(),
                coordObf.getServerTeleportPos().getZ(),
                packet.getYaw(),
                packet.getPitch()
            );
        }
        return new ServerboundMovePlayerPosRotPacket(
            packet.isOnGround(),
            reverseOffsetX,
            packet.getY(),
            reverseOffsetZ,
            packet.getYaw(),
            packet.getPitch()
        );
    }
}
