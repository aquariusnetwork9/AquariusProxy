package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddExperienceOrbPacket;

public class COAddExperienceOrbHandler implements PacketHandler<ClientboundAddExperienceOrbPacket, ServerSession> {
    @Override
    public ClientboundAddExperienceOrbPacket apply(final ClientboundAddExperienceOrbPacket packet, final ServerSession session) {
        return new ClientboundAddExperienceOrbPacket(
            packet.getEntityId(),
            session.getCoordOffset().offsetX(packet.getX()),
            packet.getY(),
            session.getCoordOffset().offsetZ(packet.getZ()),
            packet.getExp());
    }
}
