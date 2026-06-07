package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;

import static com.aquarius.Globals.MODULE;

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
