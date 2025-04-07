package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundOpenSignEditorPacket;

import static com.zenith.Globals.MODULE;

public class COOpenSignEditorHandler implements PacketHandler<ClientboundOpenSignEditorPacket, ServerSession> {
    @Override
    public ClientboundOpenSignEditorPacket apply(final ClientboundOpenSignEditorPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundOpenSignEditorPacket(
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ()),
            packet.isFrontText()
        );
    }
}
