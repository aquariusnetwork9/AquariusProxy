package com.aquarius.feature.coordobf.handlers.inbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundPickItemFromBlockPacket;

import static com.aquarius.Globals.MODULE;

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
