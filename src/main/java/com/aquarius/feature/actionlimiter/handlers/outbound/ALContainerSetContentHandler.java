package com.aquarius.feature.actionlimiter.handlers.outbound;

import com.aquarius.mc.item.ItemRegistry;
import com.aquarius.module.impl.ActionLimiter;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

public class ALContainerSetContentHandler implements PacketHandler<ClientboundContainerSetContentPacket, ServerSession> {
    @Override
    public ClientboundContainerSetContentPacket apply(final ClientboundContainerSetContentPacket packet, final ServerSession session) {
        if (!CONFIG.client.extra.actionLimiter.itemsBlacklistEnabled) return packet;
        var al = MODULE.get(ActionLimiter.class);
        for (var item : packet.getItems()) {
            if (al.isBlacklistedItem(item)) {
                var itemName = ItemRegistry.REGISTRY.get(item.getId()).name();
                al.info("Blacklisted item detected in container set content: {}", itemName);
                al.disconnect(session, "Blacklisted item: " + itemName);
                return null;
            }
        }
        if (al.isBlacklistedItem(packet.getCarriedItem())) {
            var itemName = ItemRegistry.REGISTRY.get(packet.getCarriedItem().getId()).name();
            al.info("Blacklisted item detected in container set content held item: {}", itemName);
            al.disconnect(session, "Blacklisted item: " + itemName);
            return null;
        }
        return packet;
    }
}
