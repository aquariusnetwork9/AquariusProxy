package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundSignUpdatePacket;

public class COSignUpdateHandler implements PacketHandler<ServerboundSignUpdatePacket, ServerSession> {
    @Override
    public ServerboundSignUpdatePacket apply(final ServerboundSignUpdatePacket packet, final ServerSession session) {
        return new ServerboundSignUpdatePacket(
            session.getCoordOffset().reverseOffsetX(packet.getX()),
            packet.getY(),
            session.getCoordOffset().reverseOffsetZ(packet.getZ()),
            packet.getLines(),
            packet.isFrontText());
    }
}
