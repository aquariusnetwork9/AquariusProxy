package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;

import static com.zenith.Globals.MODULE;

public class COPlayerActionHandler implements PacketHandler<ServerboundPlayerActionPacket, ServerSession> {
    @Override
    public ServerboundPlayerActionPacket apply(final ServerboundPlayerActionPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ServerboundPlayerActionPacket(
            packet.getAction(),
            coordObf.getCoordOffset(session).reverseOffsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).reverseOffsetZ(packet.getZ()),
            packet.getFace(),
            packet.getSequence()
        );
    }
}
