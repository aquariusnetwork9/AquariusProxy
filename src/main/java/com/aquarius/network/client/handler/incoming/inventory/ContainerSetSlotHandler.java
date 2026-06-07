package com.aquarius.network.client.handler.incoming.inventory;

import com.aquarius.feature.spectator.SpectatorSync;
import com.aquarius.network.client.ClientSession;
import com.aquarius.network.codec.ClientEventLoopPacketHandler;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.jspecify.annotations.NonNull;

import static com.aquarius.Globals.CACHE;

public class ContainerSetSlotHandler implements ClientEventLoopPacketHandler<ClientboundContainerSetSlotPacket, ClientSession> {
    @Override
    public boolean applyAsync(@NonNull ClientboundContainerSetSlotPacket packet, @NonNull ClientSession session) {
        CACHE.getPlayerCache().setInventorySlot(packet.getContainerId(), packet.getItem(), packet.getSlot());
        SpectatorSync.syncPlayerEquipmentWithSpectatorsFromCache();
        CACHE.getPlayerCache().getActionId().set(packet.getStateId());
        return true;
    }
}
