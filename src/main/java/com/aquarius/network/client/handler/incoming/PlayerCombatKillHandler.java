package com.aquarius.network.client.handler.incoming;

import com.aquarius.event.client.ClientDeathEvent;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerCombatKillPacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.EVENT_BUS;

public class PlayerCombatKillHandler implements ClientEventLoopPacketHandler<ClientboundPlayerCombatKillPacket, ClientSession> {

    @Override
    public boolean applyAsync(ClientboundPlayerCombatKillPacket packet, ClientSession session) {
        if (packet.getPlayerId() == CACHE.getPlayerCache().getEntityId()) {
            EVENT_BUS.postAsync(new ClientDeathEvent());
        }
        return true;
    }
}
