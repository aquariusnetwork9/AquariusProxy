package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.Proxy;
import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

public class COAddEntityHandler implements PacketHandler<ClientboundAddEntityPacket, ServerSession> {
    @Override
    public ClientboundAddEntityPacket apply(final ClientboundAddEntityPacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        if (CONFIG.client.extra.coordObfuscation.disconnectWhileEyeOfEnderPresent) {
            if (packet.getType() == EntityType.EYE_OF_ENDER) {
                coordObf.disconnect(session, coordObf.genericDisconnectReason, "An eye of ender was spawned in the world");
                return null;
            } else if (packet.getType() == EntityType.ITEM) {
                Proxy.getInstance().getClient().getClientEventLoop().execute(() -> {
                    if (coordObf.isEnderEyeInWorld()) {
                        coordObf.disconnect(session, coordObf.genericDisconnectReason, "An eye of ender is in the world");
                    }
                });
            }
        }
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
