package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Equipment;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEquipmentPacket;

import java.util.List;

import static com.zenith.Globals.MODULE;

public class COSetEquipmentHandler implements PacketHandler<ClientboundSetEquipmentPacket, ServerSession> {
    @Override
    public ClientboundSetEquipmentPacket apply(final ClientboundSetEquipmentPacket packet, final ServerSession session) {
        CoordObfuscation coordObf = MODULE.get(CoordObfuscation.class);
        List<Equipment> equips = packet.getEquipment().stream()
            .map(e -> new Equipment(e.getSlot(), coordObf.getCoordOffset(session).sanitizeItemStack(e.getItem())))
            .toList();
        return new ClientboundSetEquipmentPacket(packet.getEntityId(), equips);
    }
}
