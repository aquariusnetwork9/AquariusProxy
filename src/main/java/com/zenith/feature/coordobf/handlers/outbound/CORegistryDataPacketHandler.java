package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;

import java.util.ArrayList;
import java.util.List;

public class CORegistryDataPacketHandler implements PacketHandler<ClientboundRegistryDataPacket, ServerSession> {
    @Override
    public ClientboundRegistryDataPacket apply(final ClientboundRegistryDataPacket packet, final ServerSession session) {
        List<RegistryEntry> newRegistryEntries = new ArrayList<>(packet.getEntries().size());
        for (var entry : packet.getEntries()) {
            newRegistryEntries.add(new RegistryEntry(entry.getId(), session.getCoordOffset().offsetNbt(entry.getData())));
        }
        return new ClientboundRegistryDataPacket(
            packet.getRegistry(),
            newRegistryEntries
        );
    }
}
