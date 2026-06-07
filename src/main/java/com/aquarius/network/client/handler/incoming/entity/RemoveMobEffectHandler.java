package com.aquarius.network.client.handler.incoming.entity;

import com.aquarius.cache.data.entity.Entity;
import com.aquarius.cache.data.entity.EntityLiving;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveMobEffectPacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CLIENT_LOG;

public class RemoveMobEffectHandler implements ClientEventLoopPacketHandler<ClientboundRemoveMobEffectPacket, ClientSession> {

    @Override
    public boolean applyAsync(ClientboundRemoveMobEffectPacket packet, ClientSession session) {
        try {
            Entity entity = CACHE.getEntityCache().get(packet.getEntityId());
            if (entity instanceof EntityLiving e) {
                e.getPotionEffectMap().remove(packet.getEffect());
            } else {
                CLIENT_LOG.debug("Received ClientboundRemoveMobEffectPacket for invalid entity (id={})", packet.getEntityId());
                return true;
            }
        } catch (Exception e) {
            CLIENT_LOG.debug("Failed handling ClientboundRemoveMobEffectPacket for entity (id={})", packet.getEntityId(), e);
        }
        return true;
    }
}
