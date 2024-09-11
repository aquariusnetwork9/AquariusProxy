package com.zenith.feature.coordobf.handlers.outbound;

import com.zenith.network.registry.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerLookAtPacket;

public class COPlayerLookAtHandler implements PacketHandler<ClientboundPlayerLookAtPacket, ServerSession> {
    @Override
    public ClientboundPlayerLookAtPacket apply(final ClientboundPlayerLookAtPacket packet, final ServerSession session) {
        return new ClientboundPlayerLookAtPacket(
            packet.getOrigin(),
            session.getCoordOffset().offsetX(packet.getX()),
            packet.getY(),
            session.getCoordOffset().offsetZ(packet.getZ())
        );
    }
}
