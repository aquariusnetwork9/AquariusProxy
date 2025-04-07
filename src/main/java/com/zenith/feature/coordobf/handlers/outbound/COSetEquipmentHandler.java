package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Equipment;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEquipmentPacket;

import java.util.List;

import static com.zenith.Globals.MODULE;

public class COSetEquipmentHandler implements PacketHandler<ClientboundSetEquipmentPacket, ServerSession> {
    @Override
    public ClientboundSetEquipmentPacket apply(final ClientboundSetEquipmentPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        List<Equipment> equips = packet.getEquipment().stream()
            .map(e -> new Equipment(e.getSlot(), coordObf.getCoordOffset(session).sanitizeItemStack(e.getItem())))
            .toList();
        return new ClientboundSetEquipmentPacket(packet.getEntityId(), equips);
    }
}
