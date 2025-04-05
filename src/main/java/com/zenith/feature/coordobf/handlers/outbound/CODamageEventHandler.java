package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundDamageEventPacket;

import static com.zenith.Shared.MODULE;

public class CODamageEventHandler implements PacketHandler<ClientboundDamageEventPacket, ServerSession> {
    @Override
    public ClientboundDamageEventPacket apply(final ClientboundDamageEventPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundDamageEventPacket(
            packet.getEntityId(),
            packet.getSourceTypeId(),
            packet.getSourceCauseId(),
            packet.getSourceDirectId(),
            packet.isHasSourcePos(),
            packet.isHasSourcePos()
                ? coordObf.getCoordOffset(session).offsetX(packet.getSourcePosX())
                : 0,
            packet.getSourcePosY(),
            packet.isHasSourcePos()
                ? coordObf.getCoordOffset(session).offsetZ(packet.getSourcePosZ())
                : 0
        );
    }
}
