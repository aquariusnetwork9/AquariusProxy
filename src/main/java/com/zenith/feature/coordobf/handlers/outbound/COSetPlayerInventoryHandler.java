package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;

import static com.zenith.Globals.MODULE;

public class COSetPlayerInventoryHandler implements PacketHandler<ClientboundSetPlayerInventoryPacket, ServerSession> {
    @Override
    public ClientboundSetPlayerInventoryPacket apply(final ClientboundSetPlayerInventoryPacket packet, final ServerSession session) {
        var coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundSetPlayerInventoryPacket(
            packet.getSlot(),
            coordObf.getCoordOffset(session).sanitizeItemStack(packet.getContents())
        );
    }
}
