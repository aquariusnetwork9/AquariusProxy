package com.aquarius.network.client.handler.incoming.entity;

import com.aquarius.cache.data.entity.Entity;
import com.aquarius.cache.data.entity.EntityLiving;
import com.aquarius.cache.data.entity.PotionEffect;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket;
import org.jspecify.annotations.NonNull;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CLIENT_LOG;

public class UpdateMobEffectHandler implements ClientEventLoopPacketHandler<ClientboundUpdateMobEffectPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundUpdateMobEffectPacket packet, @NonNull ClientSession session) {
        try {
            Entity entity = CACHE.getEntityCache().get(packet.getEntityId());
            if (entity instanceof EntityLiving) {
                ((EntityLiving) entity).getPotionEffectMap().put(packet.getEffect(), new PotionEffect(
                    packet.getEffect(),
                    packet.getAmplifier(),
                    packet.getDuration(),
                    packet.isAmbient(),
                    packet.isShowParticles(),
                    packet.isShowIcon(),
                    packet.isBlend()
                ));
            } else {
                CLIENT_LOG.debug("Received ServerEntityEffectPacket for invalid entity (id={})", packet.getEntityId());
                return true;
            }
        } catch (ClassCastException e)  {
            CLIENT_LOG.debug("Received ServerEntityEffectPacket for non-equipment entity (id={})", packet.getEntityId(), e);
            return true;
        }
        return true;
    }
}
