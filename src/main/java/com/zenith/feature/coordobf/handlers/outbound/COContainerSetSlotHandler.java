package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;

import static com.zenith.Globals.MODULE;

public class COContainerSetSlotHandler implements PacketHandler<ClientboundContainerSetSlotPacket, ServerSession> {
    @Override
    public ClientboundContainerSetSlotPacket apply(final ClientboundContainerSetSlotPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        ItemStack item = coordObf.getCoordOffset(session).sanitizeItemStack(packet.getItem());
        return new ClientboundContainerSetSlotPacket(
            packet.getContainerId(),
            packet.getStateId(),
            packet.getSlot(),
            item
        );
    }
}
