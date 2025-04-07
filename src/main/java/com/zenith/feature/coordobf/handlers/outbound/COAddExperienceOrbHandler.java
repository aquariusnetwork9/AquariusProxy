package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddExperienceOrbPacket;

import static com.zenith.Globals.MODULE;

public class COAddExperienceOrbHandler implements PacketHandler<ClientboundAddExperienceOrbPacket, ServerSession> {
    @Override
    public ClientboundAddExperienceOrbPacket apply(final ClientboundAddExperienceOrbPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundAddExperienceOrbPacket(
            packet.getEntityId(),
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getExp());
    }
}
