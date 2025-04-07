package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundExplodePacket;

import java.util.stream.Collectors;

import static com.zenith.Globals.MODULE;

public class COExplodeHandler implements PacketHandler<ClientboundExplodePacket, ServerSession> {
    @Override
    public ClientboundExplodePacket apply(final ClientboundExplodePacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundExplodePacket(
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getRadius(),
            packet.getExploded().stream().map(e -> coordObf.getCoordOffset(session).offsetVector(e)).collect(Collectors.toList()),
            packet.getPushX(),
            packet.getPushY(),
            packet.getPushZ(),
            coordObf.getCoordOffset(session).offsetParticle(packet.getSmallExplosionParticles()),
            coordObf.getCoordOffset(session).offsetParticle(packet.getLargeExplosionParticles()),
            packet.getBlockInteraction(),
            packet.getExplosionSound()
        );
    }
}
