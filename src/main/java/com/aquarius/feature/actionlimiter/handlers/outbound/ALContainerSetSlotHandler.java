package com.aquarius.feature.actionlimiter.handlers.outbound;

import com.aquarius.mc.item.ItemRegistry;
import com.aquarius.module.impl.ActionLimiter;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

public class ALContainerSetSlotHandler implements PacketHandler<ClientboundContainerSetSlotPacket, ServerSession> {
    @Override
    public ClientboundContainerSetSlotPacket apply(final ClientboundContainerSetSlotPacket packet, final ServerSession session) {
        if (!CONFIG.client.extra.actionLimiter.itemsBlacklistEnabled) return packet;
        var al = MODULE.get(ActionLimiter.class);
        if (al.isBlacklistedItem(packet.getItem())) {
            var itemName = ItemRegistry.REGISTRY.get(packet.getItem().getId()).name();
            al.info("Blacklisted item detected in container set slot: {}", itemName);
            al.disconnect(session, "Blacklisted item: " + itemName);
            return null;
        }
        return packet;
    }
}
