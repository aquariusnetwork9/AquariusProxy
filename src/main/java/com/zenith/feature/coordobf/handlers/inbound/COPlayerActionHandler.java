package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;

public class COPlayerActionHandler implements PacketHandler<ServerboundPlayerActionPacket, ServerSession> {
    @Override
    public ServerboundPlayerActionPacket apply(final ServerboundPlayerActionPacket packet, final ServerSession session) {
        return new ServerboundPlayerActionPacket(
            packet.getAction(),
            session.getCoordOffset().reverseOffsetX(packet.getX()),
            packet.getY(),
            session.getCoordOffset().reverseOffsetZ(packet.getZ()),
            packet.getFace(),
            packet.getSequence()
        );
    }
}
