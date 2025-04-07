package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEntityDataPacket;

import static com.zenith.Globals.MODULE;

public class COBlockEntityDataHandler implements PacketHandler<ClientboundBlockEntityDataPacket, ServerSession> {
    @Override
    public ClientboundBlockEntityDataPacket apply(final ClientboundBlockEntityDataPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundBlockEntityDataPacket(
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getType(),
            packet.getNbt() == null ? null : coordObf.getCoordOffset(session).offsetNbt(packet.getNbt())
        );
    }
}
