package com.zenith.feature.autofish;

import com.zenith.cache.data.entity.EntityStandard;
import com.zenith.event.module.EntityFishHookSpawnEvent;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.spawn.ClientboundAddEntityPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.EVENT_BUS;

public class AutoFishAddEntityHandler implements ClientEventLoopPacketHandler<ClientboundAddEntityPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundAddEntityPacket packet, final ClientSession session) {
        var entity = CACHE.getEntityCache().get(packet.getEntityId());
        if (entity instanceof EntityStandard entityStandard && entity.getEntityType() == EntityType.FISHING_BOBBER) {
            EVENT_BUS.post(new EntityFishHookSpawnEvent(entityStandard));
        }
        return true;
    }
}
