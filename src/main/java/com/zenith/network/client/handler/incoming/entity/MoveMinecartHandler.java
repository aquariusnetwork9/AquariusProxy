package com.zenith.network.client.handler.incoming.entity;

import com.zenith.feature.spectator.SpectatorSync;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveMinecartPacket;

import static com.zenith.Globals.CACHE;

public class MoveMinecartHandler implements ClientEventLoopPacketHandler<ClientboundMoveMinecartPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundMoveMinecartPacket packet, final ClientSession session) {
        var entity = CACHE.getEntityCache().get(packet.getEntityId());
        if (entity != null && entity.getEntityType() == EntityType.MINECART) {
            // todo: directly updating cache to last lerp is not technically correct
            //   we would need to tick the lerps
            //   which would mean we need to both store these lerps and call the tick function on the entity
            //   which we do not do in our current cache or ticks models
            var lastLerp = packet.getLerpSteps().getLast();
            entity
                .setX(lastLerp.x())
                .setY(lastLerp.y())
                .setZ(lastLerp.z())
                .setVelX(lastLerp.motionX())
                .setVelY(lastLerp.motionY())
                .setVelZ(lastLerp.motionZ())
                .setYaw(lastLerp.yaw())
                .setPitch(lastLerp.pitch());
            if (!entity.getPassengerIds().isEmpty()) {
                var player = CACHE.getPlayerCache().getThePlayer();
                if (entity.getPassengerIds().contains(player.getEntityId())) {
                    player
                        .setX(lastLerp.x())
                        .setY(lastLerp.y())
                        .setZ(lastLerp.z());
                    SpectatorSync.syncPlayerPositionWithSpectators();
                }
            }
        }
        return false;
    }
}
