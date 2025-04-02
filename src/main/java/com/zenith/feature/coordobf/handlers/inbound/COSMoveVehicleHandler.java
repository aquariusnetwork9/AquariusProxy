package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundMoveVehiclePacket;

import static com.zenith.Shared.MODULE;

public class COSMoveVehicleHandler implements PacketHandler<ServerboundMoveVehiclePacket, ServerSession> {
    @Override
    public ServerboundMoveVehiclePacket apply(final ServerboundMoveVehiclePacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
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
