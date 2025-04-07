package com.zenith.network.client.handler.incoming;

import com.zenith.api.event.client.ClientDeathEvent;
import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerCombatKillPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.EVENT_BUS;

public class PlayerCombatKillHandler implements ClientEventLoopPacketHandler<ClientboundPlayerCombatKillPacket, ClientSession> {

    @Override
    public boolean applyAsync(ClientboundPlayerCombatKillPacket packet, ClientSession session) {
        if (packet.getPlayerId() == CACHE.getPlayerCache().getEntityId()) {
            EVENT_BUS.postAsync(new ClientDeathEvent());
        }
        return true;
    }
}
