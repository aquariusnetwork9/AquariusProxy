package com.zenith.network.client.handler.postoutgoing;

import com.zenith.api.network.ClientEventLoopPacketHandler;
import com.zenith.api.network.client.ClientSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundSetCreativeModeSlotPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.feature.spectator.SpectatorSync.syncPlayerEquipmentWithSpectatorsFromCache;

public class PostOutgoingSetCreativeModeSlotHandler implements ClientEventLoopPacketHandler<ServerboundSetCreativeModeSlotPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ServerboundSetCreativeModeSlotPacket packet, final ClientSession session) {
        CACHE.getPlayerCache().getInventoryCache().handleSetCreativeModeSlot(packet);
        syncPlayerEquipmentWithSpectatorsFromCache();
        return true;
    }
}
