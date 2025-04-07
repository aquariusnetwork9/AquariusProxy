package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
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
