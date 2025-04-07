package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.api.network.PacketHandler;
import com.zenith.api.network.server.ServerSession;
import com.zenith.module.impl.CoordObfuscator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerLookAtPacket;

import static com.zenith.Globals.MODULE;

public class COPlayerLookAtHandler implements PacketHandler<ClientboundPlayerLookAtPacket, ServerSession> {
    @Override
    public ClientboundPlayerLookAtPacket apply(final ClientboundPlayerLookAtPacket packet, final ServerSession session) {
        CoordObfuscator coordObf = MODULE.get(CoordObfuscator.class);
        return new ClientboundPlayerLookAtPacket(
            packet.getOrigin(),
            coordObf.getCoordOffset(session).offsetX(packet.getX()),
            packet.getY(),
            coordObf.getCoordOffset(session).offsetZ(packet.getZ())
        );
    }
}
