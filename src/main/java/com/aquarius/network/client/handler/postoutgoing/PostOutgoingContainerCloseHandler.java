package com.aquarius.network.client.handler.postoutgoing;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClosePacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.feature.spectator.SpectatorSync.syncPlayerEquipmentWithSpectatorsFromCache;

public class PostOutgoingContainerCloseHandler implements ClientEventLoopPacketHandler<ServerboundContainerClosePacket, ClientSession> {
    @Override
    public boolean applyAsync(final ServerboundContainerClosePacket packet, final ClientSession session) {
        CACHE.getPlayerCache().closeContainer(packet.getContainerId());
        syncPlayerEquipmentWithSpectatorsFromCache();
        return true;
    }
}
