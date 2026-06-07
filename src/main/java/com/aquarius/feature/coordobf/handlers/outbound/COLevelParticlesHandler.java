package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelParticlesPacket;

import static com.aquarius.Globals.MODULE;

public class COLevelParticlesHandler implements PacketHandler<ClientboundLevelParticlesPacket, ServerSession> {
    @Override
    public ClientboundLevelParticlesPacket apply(final ClientboundLevelParticlesPacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundLevelParticlesPacket(
            coordObf.getCoordOffset(session).offsetParticle(packet.getParticle()),
            packet.isLongDistance(),
            packet.isAlwaysShow(),
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
