package com.aquarius.feature.coordobf.handlers.inbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;

import static com.aquarius.Globals.MODULE;

public class COPlayerActionHandler implements PacketHandler<ServerboundPlayerActionPacket, ServerSession> {
    @Override
    public ServerboundPlayerActionPacket apply(final ServerboundPlayerActionPacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ServerboundPlayerActionPacket(
            packet.getAction(),
            packet.getX() != 0 ? coordObf.getCoordOffset(session).reverseOffsetX(packet.getX()) : 0,
            packet.getY(),
            packet.getZ() != 0 ? coordObf.getCoordOffset(session).reverseOffsetZ(packet.getZ()) : 0,
            packet.getFace(),
            packet.getSequence()
        );
    }
}
