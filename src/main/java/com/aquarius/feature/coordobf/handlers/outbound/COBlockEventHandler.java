package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEventPacket;

import static com.aquarius.Globals.MODULE;

public class COBlockEventHandler implements PacketHandler<ClientboundBlockEventPacket, ServerSession> {
    @Override
    public ClientboundBlockEventPacket apply(final ClientboundBlockEventPacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundBlockEventPacket(
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getRawType(),
            packet.getRawValue(),
            packet.getBlockId()
        );
    }
}
