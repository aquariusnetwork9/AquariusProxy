package com.aquarius.network.client.handler.postoutgoing;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundSetCreativeModeSlotPacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.feature.spectator.SpectatorSync.syncPlayerEquipmentWithSpectatorsFromCache;

public class PostOutgoingSetCreativeModeSlotHandler implements ClientEventLoopPacketHandler<ServerboundSetCreativeModeSlotPacket, ClientSession> {
    @Override
    public boolean applyAsync(final ServerboundSetCreativeModeSlotPacket packet, final ClientSession session) {
        CACHE.getPlayerCache().getInventoryCache().handleSetCreativeModeSlot(packet);
        syncPlayerEquipmentWithSpectatorsFromCache();
        return true;
    }
}
