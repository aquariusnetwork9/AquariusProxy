package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.MinecartStep;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveMinecartPacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.MODULE;

public class COMoveMinecartHandler implements PacketHandler<ClientboundMoveMinecartPacket, ServerSession> {
    @Override
    public ClientboundMoveMinecartPacket apply(final ClientboundMoveMinecartPacket packet, final ServerSession session) {
        var coordObf = MODULE.get(CoordObfuscation.class);
        var entity = CACHE.getEntityCache().get(packet.getEntityId());
        if (entity == null || entity.getEntityType() != EntityType.MINECART) {
            return null;
        }
        return new ClientboundMoveMinecartPacket(
            packet.getEntityId(),
            packet.getLerpSteps().stream()
                .map(step ->
                    new MinecartStep(
                        coordObf.getCoordOffset(session).offsetX(step.x()),
                        step.y(),
                        coordObf.getCoordOffset(session).offsetZ(step.z()),
                        step.motionX(),
                        step.motionY(),
                        step.motionZ(),
                        step.yaw(),
                        step.pitch(),
                        step.weight()
                    ))
                .toList()
        );
    }
}
