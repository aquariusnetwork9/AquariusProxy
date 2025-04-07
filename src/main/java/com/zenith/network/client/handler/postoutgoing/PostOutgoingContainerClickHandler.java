package com.zenith.network.client.handler.postoutgoing;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.feature.spectator.SpectatorSync.syncPlayerEquipmentWithSpectatorsFromCache;

public class PostOutgoingContainerClickHandler implements ClientEventLoopPacketHandler<ServerboundContainerClickPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ServerboundContainerClickPacket packet, final ClientSession session) {
        CACHE.getPlayerCache().getInventoryCache().handleContainerClick(packet);
        syncPlayerEquipmentWithSpectatorsFromCache();
        return true;
    }
}
