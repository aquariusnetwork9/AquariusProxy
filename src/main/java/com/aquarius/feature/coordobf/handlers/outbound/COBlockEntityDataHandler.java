package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockEntityDataPacket;

import static com.aquarius.Globals.MODULE;

public class COBlockEntityDataHandler implements PacketHandler<ClientboundBlockEntityDataPacket, ServerSession> {
    @Override
    public ClientboundBlockEntityDataPacket apply(final ClientboundBlockEntityDataPacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundBlockEntityDataPacket(
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getType(),
            packet.getNbt() == null ? null : coordObf.getCoordOffset(session).offsetNbt(packet.getNbt())
        );
    }
}
