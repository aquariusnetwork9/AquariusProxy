package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEventPacket;

import static com.zenith.Globals.MODULE;

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
