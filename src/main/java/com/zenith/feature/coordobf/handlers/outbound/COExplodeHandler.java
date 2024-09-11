package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundExplodePacket;

import java.util.stream.Collectors;

public class COExplodeHandler implements PacketHandler<ClientboundExplodePacket, ServerSession> {
    @Override
    public ClientboundExplodePacket apply(final ClientboundExplodePacket packet, final ServerSession session) {
        return new ClientboundExplodePacket(
            session.getCoordOffset().offsetX(packet.getX()),
            packet.getY(),
            session.getCoordOffset().offsetZ(packet.getZ()),
            packet.getRadius(),
            packet.getExploded().stream().map(e -> session.getCoordOffset().offsetVector(e)).collect(Collectors.toList()),
            packet.getPushX(),
            packet.getPushY(),
            packet.getPushZ(),
            packet.getSmallExplosionParticles(),
            packet.getLargeExplosionParticles(),
            packet.getBlockInteraction(),
            packet.getExplosionSound()
        );
    }
}
