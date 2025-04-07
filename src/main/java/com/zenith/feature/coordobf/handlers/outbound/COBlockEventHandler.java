package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
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
