package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;

public class COContainerSetSlotHandler implements PacketHandler<ClientboundContainerSetSlotPacket, ServerSession> {
    @Override
    public ClientboundContainerSetSlotPacket apply(final ClientboundContainerSetSlotPacket packet, final ServerSession session) {
        ItemStack item = session.getCoordOffset().sanitizeItemStack(packet.getItem());
        return new ClientboundContainerSetSlotPacket(
            packet.getContainerId(),
            packet.getStateId(),
            packet.getSlot(),
            item
        );
    }
}
