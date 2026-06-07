package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;

import java.util.Arrays;
import java.util.List;

import static com.aquarius.Globals.MODULE;

public class COContainerSetContentHandler implements PacketHandler<ClientboundContainerSetContentPacket, ServerSession> {
    @Override
    public ClientboundContainerSetContentPacket apply(final ClientboundContainerSetContentPacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        List<ItemStack> itemStacks = Arrays.asList(packet.getItems());
        ItemStack[] stacks = itemStacks.stream()
            .map(is -> coordObf.getCoordOffset(session).sanitizeItemStack(is)).toList().toArray(new ItemStack[0]);
        ItemStack carriedItem = coordObf.getCoordOffset(session).sanitizeItemStack(packet.getCarriedItem());
        return new ClientboundContainerSetContentPacket(
            packet.getContainerId(),
            packet.getStateId(),
            stacks,
            carriedItem
        );
    }
}
