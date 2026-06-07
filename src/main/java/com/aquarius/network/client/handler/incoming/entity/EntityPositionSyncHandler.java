package com.aquarius.network.client.handler.incoming.entity;

import com.aquarius.cache.data.entity.Entity;
import com.aquarius.feature.spectator.SpectatorSync;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CLIENT_LOG;

public class EntityPositionSyncHandler implements ClientEventLoopPacketHandler<ClientboundEntityPositionSyncPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundEntityPositionSyncPacket packet, final ClientSession session) {
        Entity entity = CACHE.getEntityCache().get(packet.getId());
        if (entity != null) {
            entity
                .setX(packet.getX())
                .setY(packet.getY())
                .setZ(packet.getZ())
                .setVelX(packet.getDeltaX())
                .setVelY(packet.getDeltaY())
                .setVelZ(packet.getDeltaZ())
                .setYaw(packet.getYaw())
                .setPitch(packet.getPitch());
            if (!entity.getPassengerIds().isEmpty()) {
                var player = CACHE.getPlayerCache().getThePlayer();
                if (entity.getPassengerIds().contains(player.getEntityId())) {
                    player
                        .setX(packet.getX())
                        .setY(packet.getY())
                        .setZ(packet.getZ());
                    SpectatorSync.syncPlayerPositionWithSpectators();
                }
            }
            return true;
        } else {
            CLIENT_LOG.debug("Received ClientboundEntityPositionSyncPacket for invalid entity (id={})", packet.getId());
            return true;
        }
    }
}
