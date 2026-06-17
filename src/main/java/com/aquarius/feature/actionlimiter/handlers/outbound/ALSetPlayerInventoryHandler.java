package com.aquarius.feature.actionlimiter.handlers.outbound;

import com.aquarius.mc.item.ItemRegistry;
import com.aquarius.module.impl.ActionLimiter;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

public class ALSetPlayerInventoryHandler implements PacketHandler<ClientboundSetPlayerInventoryPacket, ServerSession> {
    @Override
    public ClientboundSetPlayerInventoryPacket apply(final ClientboundSetPlayerInventoryPacket packet, final ServerSession session) {
        if (!CONFIG.client.extra.actionLimiter.itemsBlacklistEnabled
            || !CONFIG.client.extra.actionLimiter.blacklistedItemDisconnect) return packet;
        var al = MODULE.get(ActionLimiter.class);
        if (al.isBlacklistedItem(packet.getContents())) {
            var itemName = ItemRegistry.REGISTRY.get(packet.getContents().getId()).name();
            al.info("Blacklisted item detected in set player inventory: {}", itemName);
            al.disconnect(session, "Blacklisted item: " + itemName);
            return null;
        }
        return packet;
    }
}
