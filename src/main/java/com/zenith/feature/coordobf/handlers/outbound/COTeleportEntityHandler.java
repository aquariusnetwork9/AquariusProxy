package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.cache.data.entity.Entity;
import com.zenith.cache.data.entity.EntityStandard;
import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;

import static com.zenith.Shared.CACHE;
import static com.zenith.Shared.MODULE;

public class COTeleportEntityHandler implements PacketHandler<ClientboundTeleportEntityPacket, ServerSession> {
    @Override
    public ClientboundTeleportEntityPacket apply(final ClientboundTeleportEntityPacket packet, final ServerSession session) {
        Entity entity = CACHE.getEntityCache().get(packet.getId());
        if (entity instanceof EntityStandard e) {
            if (e.getEntityType() == EntityType.EYE_OF_ENDER) {
                return null;
            }
        }
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundTeleportEntityPacket(
            packet.getId(),
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.getDeltaX(),
            packet.getDeltaY(),
            packet.getDeltaZ(),
            packet.getYaw(),
            packet.getPitch(),
            packet.isOnGround()
        );
    }
}
