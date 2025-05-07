package com.zenith.network.client.handler.incoming.inventory;

import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.feature.spectator.SpectatorSync.syncPlayerEquipmentWithSpectatorsFromCache;

public class SetPlayerInventoryHandler implements ClientEventLoopPacketHandler<ClientboundSetPlayerInventoryPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundSetPlayerInventoryPacket packet, final ClientSession session) {
        CACHE.getPlayerCache().getInventoryCache().getPlayerInventory().setItemStack(packet.getSlot(), packet.getContents());
        syncPlayerEquipmentWithSpectatorsFromCache();
        return true;
    }
}
