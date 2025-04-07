package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;

import static com.zenith.Globals.MODULE;

public class COAddEntityHandler implements PacketHandler<ClientboundAddEntityPacket, ServerSession> {
    @Override
    public ClientboundAddEntityPacket apply(final ClientboundAddEntityPacket packet, final ServerSession session) {
        if (packet.getType() == EntityType.EYE_OF_ENDER) {
            return null;
        }
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundAddEntityPacket(
            packet.getEntityId(),
            packet.getUuid(),
            packet.getType(),
            packet.getData(),
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getYaw(),
            packet.getHeadYaw(),
            packet.getPitch(),
            packet.getMotionX(),
            packet.getMotionY(),
            packet.getMotionZ());
    }
}
