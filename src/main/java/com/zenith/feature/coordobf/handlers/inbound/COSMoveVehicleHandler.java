package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundMoveVehiclePacket;

import static com.zenith.Globals.MODULE;

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
            packet.getPitch()
        );
    }
}
