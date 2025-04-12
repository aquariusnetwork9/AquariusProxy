package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundSignUpdatePacket;

import static com.zenith.Globals.MODULE;

public class COSignUpdateHandler implements PacketHandler<ServerboundSignUpdatePacket, ServerSession> {
    @Override
    public ServerboundSignUpdatePacket apply(final ServerboundSignUpdatePacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ServerboundSignUpdatePacket(
            coordObf.getCoordOffset(session).reverseOffsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).reverseOffsetZ(packet.getZ()),
            packet.getLines(),
            packet.isFrontText());
    }
}
