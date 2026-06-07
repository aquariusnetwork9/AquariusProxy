package com.aquarius.network.client.handler.incoming.inventory;

import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.feature.spectator.SpectatorSync.syncPlayerEquipmentWithSpectatorsFromCache;

public class ContainerCloseHandler implements ClientEventLoopPacketHandler<ClientboundContainerClosePacket, ClientSession> {
    @Override
    public boolean applyAsync(final ClientboundContainerClosePacket packet, final ClientSession session) {
        CACHE.getPlayerCache().closeContainer(packet.getContainerId());
        syncPlayerEquipmentWithSpectatorsFromCache();
        return true;
    }
}
