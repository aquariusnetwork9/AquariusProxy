package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEventPacket;

import static com.zenith.Globals.MODULE;

public class COBlockEventHandler implements PacketHandler<ClientboundBlockEventPacket, ServerSession> {
    @Override
    public ClientboundBlockEventPacket apply(final ClientboundBlockEventPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundBlockEventPacket(
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getType(),
            packet.getValue(),
            packet.getBlockId()
        );
    }
}
