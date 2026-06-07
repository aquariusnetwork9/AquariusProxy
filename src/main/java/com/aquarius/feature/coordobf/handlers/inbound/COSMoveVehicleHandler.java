package com.aquarius.feature.coordobf.handlers.inbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundMoveVehiclePacket;

import static com.aquarius.Globals.MODULE;

public class COSMoveVehicleHandler implements PacketHandler<ServerboundMoveVehiclePacket, ServerSession> {
    @Override
    public ServerboundMoveVehiclePacket apply(final ServerboundMoveVehiclePacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        coordObf.playerMovePos(
            session,
            coordObf.getCoordOffset(session).reverseOffsetX(packet.getX()),
            coordObf.getCoordOffset(session).reverseOffsetZ(packet.getZ()));
        return new ServerboundMoveVehiclePacket(
            coordObf.getCoordOffset(session).reverseOffsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).reverseOffsetZ(packet.getZ()),
            packet.getYaw(),
            packet.getPitch(),
            packet.isOnGround()
        );
    }
}
