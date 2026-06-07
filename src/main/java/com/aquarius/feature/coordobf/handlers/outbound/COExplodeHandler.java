package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundExplodePacket;

import static com.aquarius.Globals.MODULE;

public class COExplodeHandler implements PacketHandler<ClientboundExplodePacket, ServerSession> {
    @Override
    public ClientboundExplodePacket apply(final ClientboundExplodePacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundExplodePacket(
            coordObf.getCoordOffset(session).offsetX(packet.getCenterX()),
            packet.getCenterY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getCenterZ()),
            packet.getPlayerKnockback(),
            coordObf.getCoordOffset(session).offsetParticle(packet.getExplosionParticle()),
            packet.getExplosionSound()
        );
    }
}
