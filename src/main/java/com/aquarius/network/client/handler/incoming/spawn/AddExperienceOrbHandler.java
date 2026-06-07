package com.aquarius.network.client.handler.incoming.spawn;

import com.aquarius.cache.data.entity.EntityExperienceOrb;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddExperienceOrbPacket;
import org.jspecify.annotations.NonNull;

import static com.aquarius.Globals.CACHE;

public class AddExperienceOrbHandler implements ClientEventLoopPacketHandler<ClientboundAddExperienceOrbPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundAddExperienceOrbPacket packet, @NonNull ClientSession session) {
        CACHE.getEntityCache().add(
            new EntityExperienceOrb()
                .setExp(packet.getExp())
                .setEntityId(packet.getEntityId())
                .setX(packet.getX())
                .setY(packet.getY())
                .setZ(packet.getZ())
                .setEntityType(EntityType.EXPERIENCE_ORB)
        );
        return true;
    }
}
