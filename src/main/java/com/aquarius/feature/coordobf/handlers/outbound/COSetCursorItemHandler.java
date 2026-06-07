package com.aquarius.feature.coordobf.handlers.outbound;

import com.aquarius.module.impl.CoordObfuscation;
import com.aquarius.network.codec.PacketHandler;
import com.aquarius.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetCursorItemPacket;

import static com.aquarius.Globals.MODULE;

public class COSetCursorItemHandler implements PacketHandler<ClientboundSetCursorItemPacket, ServerSession> {
    @Override
    public ClientboundSetCursorItemPacket apply(final ClientboundSetCursorItemPacket packet, final ServerSession session) {
        var coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundSetCursorItemPacket(
            coordObf.getCoordOffset(session).sanitizeItemStack(packet.getContents())
        );
    }
}
