package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscation;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetCursorItemPacket;

import static com.zenith.Globals.MODULE;

public class COSetCursorItemHandler implements PacketHandler<ClientboundSetCursorItemPacket, ServerSession> {
    @Override
    public ClientboundSetCursorItemPacket apply(final ClientboundSetCursorItemPacket packet, final ServerSession session) {
        var coordObf = MODULE.get(CoordObfuscation.class);
        return new ClientboundSetCursorItemPacket(
            coordObf.getCoordOffset(session).sanitizeItemStack(packet.getContents())
        );
    }
}
