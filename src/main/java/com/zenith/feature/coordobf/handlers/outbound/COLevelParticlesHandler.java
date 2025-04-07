package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelParticlesPacket;

import static com.zenith.Globals.MODULE;

public class COLevelParticlesHandler implements PacketHandler<ClientboundLevelParticlesPacket, ServerSession> {
    @Override
    public ClientboundLevelParticlesPacket apply(final ClientboundLevelParticlesPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundLevelParticlesPacket(
            coordObf.getCoordOffset(session).offsetParticle(packet.getParticle()),
            packet.isLongDistance(),
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getOffsetX(),
            packet.getOffsetY(),
            packet.getOffsetZ(),
            packet.getVelocityOffset(),
            packet.getAmount()
        );
    }
}
