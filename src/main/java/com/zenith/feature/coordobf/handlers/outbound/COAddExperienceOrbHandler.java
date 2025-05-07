package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddExperienceOrbPacket;

import static com.zenith.Globals.MODULE;

public class COAddExperienceOrbHandler implements PacketHandler<ClientboundAddExperienceOrbPacket, ServerSession> {
    @Override
    public ClientboundAddExperienceOrbPacket apply(final ClientboundAddExperienceOrbPacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundAddExperienceOrbPacket(
            packet.getEntityId(),
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getExp());
    }
}
