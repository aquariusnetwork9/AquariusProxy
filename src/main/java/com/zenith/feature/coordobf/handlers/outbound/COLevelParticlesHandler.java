package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.ItemParticleData;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.Particle;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.VibrationParticleData;
import org.geysermc.mcprotocollib.protocol.data.game.level.particle.positionsource.BlockPositionSource;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelParticlesPacket;

import static com.zenith.Shared.MODULE;

public class COLevelParticlesHandler implements PacketHandler<ClientboundLevelParticlesPacket, ServerSession> {
    @Override
    public ClientboundLevelParticlesPacket apply(final ClientboundLevelParticlesPacket packet, final ServerSession session) {
        Particle particle = packet.getParticle();
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        if (packet.getParticle().getData() instanceof ItemParticleData itemParticleData) {
            particle = new Particle(
                particle.getType(),
                new ItemParticleData(
                    coordObf.getCoordOffset(session).sanitizeItemStack(itemParticleData.getItemStack())
                )
            );
        } else if (packet.getParticle().getData() instanceof VibrationParticleData vibrationParticleData) {
            var positionSrc = vibrationParticleData.getPositionSource();
            if (positionSrc instanceof BlockPositionSource bps) {
                positionSrc = new BlockPositionSource(
                    coordObf.getCoordOffset(session).offsetVector(bps.getPosition())
                );
            }
            particle = new Particle(
                particle.getType(),
                new VibrationParticleData(
                    positionSrc,
                    vibrationParticleData.getArrivalTicks()
                )
            );
        }
        return new ClientboundLevelParticlesPacket(
            particle,
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
