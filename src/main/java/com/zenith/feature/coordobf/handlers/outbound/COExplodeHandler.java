package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.Particle;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundExplodePacket;

import static com.zenith.Shared.MODULE;

public class COExplodeHandler implements PacketHandler<ClientboundExplodePacket, ServerSession> {
    @Override
    public ClientboundExplodePacket apply(final ClientboundExplodePacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundExplodePacket(
            coordObf.getCoordOffset(session).offsetX(packet.getCenterX()),
            packet.getCenterY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getCenterZ()),
            packet.getPlayerKnockback(),
            new Particle(packet.getExplosionParticle().getType(), packet.getExplosionParticle().getData()),
            packet.getExplosionSound()
        );
    }
}
