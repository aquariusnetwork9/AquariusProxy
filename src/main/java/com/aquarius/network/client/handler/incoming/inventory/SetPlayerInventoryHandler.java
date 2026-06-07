package com.aquarius.network.client.handler.incoming.inventory;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.feature.spectator.SpectatorSync.syncPlayerEquipmentWithSpectatorsFromCache;

public class SetPlayerInventoryHandler implements ClientEventLoopPacketHandler<ClientboundSetPlayerInventoryPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundSetPlayerInventoryPacket packet, final ClientSession session) {
        CACHE.getPlayerCache().getInventoryCache().getPlayerInventory().setItemStack(packet.getSlot(), packet.getContents());
        syncPlayerEquipmentWithSpectatorsFromCache();
        return true;
    }
}
