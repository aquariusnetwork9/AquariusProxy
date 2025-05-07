package com.zenith.feature.coordobf.handlers.inbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundPickItemFromBlockPacket;

import static com.zenith.Globals.MODULE;

public class COPickItemFromBlockHandler implements PacketHandler<ServerboundPickItemFromBlockPacket, ServerSession> {
    @Override
    public ServerboundPickItemFromBlockPacket apply(final ServerboundPickItemFromBlockPacket packet, final ServerSession session) {
        var coordObf = MODULE.get(CoordObfuscation.class);
        return new ServerboundPickItemFromBlockPacket(
            coordObf.getCoordOffset(session).reverseOffsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).reverseOffsetZ(packet.getZ()),
            packet.isIncludeData()
        );
    }
}
