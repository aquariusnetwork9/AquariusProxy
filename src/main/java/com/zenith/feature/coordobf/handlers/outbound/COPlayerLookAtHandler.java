package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.module.impl.CoordObfuscator;
import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerLookAtPacket;

import static com.zenith.Shared.MODULE;

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
