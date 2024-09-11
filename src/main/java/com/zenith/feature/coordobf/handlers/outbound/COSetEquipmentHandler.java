package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Equipment;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEquipmentPacket;

import java.util.List;

public class COSetEquipmentHandler implements PacketHandler<ClientboundSetEquipmentPacket, ServerSession> {
    @Override
    public ClientboundSetEquipmentPacket apply(final ClientboundSetEquipmentPacket packet, final ServerSession session) {
        List<Equipment> equips = packet.getEquipment().stream()
            .map(e -> new Equipment(e.getSlot(), session.getCoordOffset().sanitizeItemStack(e.getItem())))
            .toList();
        return new ClientboundSetEquipmentPacket(packet.getEntityId(), equips);
    }
}
