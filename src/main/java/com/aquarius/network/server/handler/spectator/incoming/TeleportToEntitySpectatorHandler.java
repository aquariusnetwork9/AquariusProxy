package com.aquarius.network.server.handler.spectator.incoming;

import com.aquarius.cache.data.entity.Entity;
import com.aquarius.feature.spectator.SpectatorSync;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSetCameraPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundTeleportToEntityPacket;

import static com.aquarius.Globals.CACHE;

public class TeleportToEntitySpectatorHandler implements PacketHandler<ServerboundTeleportToEntityPacket, ServerSession> {
    @Override
    public ServerboundTeleportToEntityPacket apply(final ServerboundTeleportToEntityPacket packet, final ServerSession session) {
        final Entity targetEntity = CACHE.getEntityCache().get(packet.getTarget());
        if (targetEntity != null) {
            if (session.hasCameraTarget()) {
                session.setCameraTarget(null);
                session.send(new ClientboundSetCameraPacket(session.getSpectatorSelfEntityId()));
            }
            SpectatorSync.syncSpectatorPositionToEntity(session, targetEntity);
        }
        return null;
    }
}
