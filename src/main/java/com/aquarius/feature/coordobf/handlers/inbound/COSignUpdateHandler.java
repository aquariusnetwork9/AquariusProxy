package com.aquarius.feature.coordobf.handlers.inbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundSignUpdatePacket;

import static com.aquarius.Globals.MODULE;

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
