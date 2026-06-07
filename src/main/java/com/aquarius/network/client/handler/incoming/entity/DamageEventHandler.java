package com.aquarius.network.client.handler.incoming.entity;

import com.aquarius.cache.data.entity.Entity;
import com.aquarius.cache.data.entity.EntityPlayer;
import com.aquarius.event.module.ServerPlayerAttackedUsEvent;
import com.aquarius.mc.damage_type.DamageTypeRegistry;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundDamageEventPacket;

import static com.aquarius.Globals.*;

public class DamageEventHandler implements ClientEventLoopPacketHandler<ClientboundDamageEventPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundDamageEventPacket packet, final ClientSession session) {
        var playerAttackDamageType = DamageTypeRegistry.REGISTRY.get("player_attack");
        if (playerAttackDamageType != null && packet.getSourceTypeId() == playerAttackDamageType.id()) {
            if (packet.getEntityId() == CACHE.getPlayerCache().getEntityId()) {
                Entity attacker = CACHE.getEntityCache().get(packet.getSourceCauseId());
                if (attacker instanceof EntityPlayer attackerPlayer && attackerPlayer.getEntityId() != CACHE.getPlayerCache().getEntityId()) {
                    EVENT_BUS.postAsync(new ServerPlayerAttackedUsEvent(attackerPlayer, packet.getSourcePosition()));
                    CLIENT_LOG.debug("Player {} attacked us", attackerPlayer.getEntityId());
                }
            }
        }
        return true;
    }
}
