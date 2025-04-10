package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.feature.coordobf.CoordOffset;
import com.zenith.module.impl.CoordObfuscator;
import com.zenith.util.math.MathHelper;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.MODULE;

public class COMovePlayerPosRotHandler implements PacketHandler<ServerboundMovePlayerPosRotPacket, ServerSession> {
    @Override
    public ServerboundMovePlayerPosRotPacket apply(final ServerboundMovePlayerPosRotPacket packet, final ServerSession session) {
        var coordObf = MODULE.get(CoordObfuscator.class);
        var state= coordObf.getPlayerState(session);
        if (!state.isInGame()) {
            if (session.isSpectator()) {
                state.setInGame(true);
                return packet;
            } else {
                if (MathHelper.isInRange(packet.getX(), coordObf.getCoordOffset(session).offsetX(CACHE.getPlayerCache().getX()), CoordOffset.EPSILON * 2)
                    && packet.getY() == CACHE.getPlayerCache().getY()
                    && MathHelper.isInRange(packet.getZ(), coordObf.getCoordOffset(session).offsetZ(CACHE.getPlayerCache().getZ()), CoordOffset.EPSILON * 2)) {
                    state.setInGame(true);
                    // correct server teleport position
                    return new ServerboundMovePlayerPosRotPacket(
                        false,
                        CACHE.getPlayerCache().getX(),
                        CACHE.getPlayerCache().getY(),
                        CACHE.getPlayerCache().getZ(),
                        packet.getYaw(),
                        packet.getPitch()
                    );
                } else {
                    coordObf.info("Received {} pos: {} {} {} but expected: {} {} {}",
                                    session.getName(),
                                    packet.getX(),
                                    packet.getY(),
                                    packet.getZ(),
                                    coordObf.getCoordOffset(session).offsetX(CACHE.getPlayerCache().getX()),
                                    CACHE.getPlayerCache().getY(),
                                    coordObf.getCoordOffset(session).offsetZ(CACHE.getPlayerCache().getZ()));
                    return null;
                }
            }
        }
        double reverseOffsetX = coordObf.getCoordOffset(session).reverseOffsetX(packet.getX());
        double reverseOffsetZ = coordObf.getCoordOffset(session).reverseOffsetZ(packet.getZ());
        var serverTp = state.getServerTeleport();
        if (serverTp != null && !session.isSpectator()) {
            if (MathHelper.isInRange(reverseOffsetX, serverTp.x(), CoordOffset.EPSILON * 2)
                && packet.getY() == serverTp.y()
                && MathHelper.isInRange(reverseOffsetZ, serverTp.z(), CoordOffset.EPSILON * 2)
            ) {
                coordObf.info("[{}] Accepting server teleport {}", session.getName(), serverTp.id());
                state.setServerTeleport(null);
                return new ServerboundMovePlayerPosRotPacket(
                    false,
                    serverTp.x(),
                    serverTp.y(),
                    serverTp.z(),
                    packet.getYaw(),
                    packet.getPitch()
                );
            } else {
                coordObf.info("[{}] Did not accept server teleport: {}, they sent: {} {} {}",
                    session.getName(),
                    serverTp,
                    reverseOffsetX,
                    packet.getY(),
                    reverseOffsetZ
                );
                return null;
            }
        }
        coordObf.playerMovePos(session, reverseOffsetX, reverseOffsetZ);
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
