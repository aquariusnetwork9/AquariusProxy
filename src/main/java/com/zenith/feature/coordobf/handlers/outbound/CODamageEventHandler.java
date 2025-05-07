package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.cloudburstmc.math.vector.Vector3d;
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
            packet.getSourcePosition() == null
                ? null
                : Vector3d.from(
                coordObf.getCoordOffset(session).offsetX(packet.getSourcePosition().getX()),
                packet.getSourcePosition().getY(),
                coordObf.getCoordOffset(session).offsetZ(packet.getSourcePosition().getZ()))
        );
    }
}
