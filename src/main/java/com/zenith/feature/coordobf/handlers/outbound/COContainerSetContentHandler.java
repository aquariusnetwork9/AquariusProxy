package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;

import java.util.Arrays;
import java.util.List;

public class COContainerSetContentHandler implements PacketHandler<ClientboundContainerSetContentPacket, ServerSession> {
    @Override
    public ClientboundContainerSetContentPacket apply(final ClientboundContainerSetContentPacket packet, final ServerSession session) {
        List<ItemStack> itemStacks = Arrays.asList(packet.getItems());
        ItemStack[] stacks = itemStacks.stream()
            .map(is -> session.getCoordOffset().sanitizeItemStack(is)).toList().toArray(new ItemStack[0]);
        ItemStack carriedItem = session.getCoordOffset().sanitizeItemStack(packet.getCarriedItem());
        return new ClientboundContainerSetContentPacket(
            packet.getContainerId(),
            packet.getStateId(),
            stacks,
            carriedItem
        );
    }
}
