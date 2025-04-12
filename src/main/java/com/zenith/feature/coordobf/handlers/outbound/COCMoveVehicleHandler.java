package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveVehiclePacket;

import static com.zenith.Globals.MODULE;

public class COCMoveVehicleHandler implements PacketHandler<ClientboundMoveVehiclePacket, ServerSession> {
    @Override
    public ClientboundMoveVehiclePacket apply(final ClientboundMoveVehiclePacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        coordObf.playerMovePos(session, packet.getX(), packet.getZ());
        return new ClientboundMoveVehiclePacket(
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getYaw(),
            packet.getPitch()
        );
    }
}
